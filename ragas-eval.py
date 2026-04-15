# ragas_eval.py — called by Maven exec plugin or superpowers tool
import os
import requests
from ragas import evaluate
from ragas.metrics import faithfulness, context_precision, answer_relevancy
from ragas.llms import LangchainLLMWrapper
from ragas.embeddings import LangchainEmbeddingsWrapper
from langchain_openai import ChatOpenAI, OpenAIEmbeddings
from datasets import Dataset

os.environ["OPENAI_API_KEY"] = "Your-api-key"

# LLM — GPT-4o-mini (cheap, fast, works well for RAGAS evaluation)
llm = LangchainLLMWrapper(
    ChatOpenAI(
        model="gpt-4o-mini",
        timeout=60,
        max_retries=3
    )
)

# Embeddings — OpenAI text-embedding-3-small (cheapest OpenAI embedding model)
embeddings = LangchainEmbeddingsWrapper(
    OpenAIEmbeddings(model="text-embedding-3-small")
)

# Load eval dataset from JSON
dataset = Dataset.from_json("src/main/resources/eval/eval-dataset.json")

# Collect results into lists (HuggingFace datasets are immutable)
questions     = []
answers       = []
contexts      = []
ground_truths = []

for item in dataset:
    response = requests.post(
        "http://localhost:8080/api/rag/query",
        params={"q": item["question"]}  # matches @RequestParam("q")
    )

    if response.status_code != 200:
        print(f"SKIPPING — API error for: {item['question']}")
        print(f"  Status: {response.status_code}, Body: {response.text[:200]}")
        continue

    data = response.json()
    questions.append(item["question"])
    answers.append(data["answer"])
    contexts.append([c["chunkText"] for c in data["citations"]])
    ground_truths.append(item.get("ground_truth", ""))

# Debug — print what's being sent to RAGAS before evaluating
print("=== DEBUG ===")
for i, (q, a, c, g) in enumerate(zip(questions, answers, contexts, ground_truths)):
    print(f"\nRow {i}:")
    print(f"  question:     {q}")
    print(f"  answer:       {a[:80]}...")
    print(f"  contexts:     {c}")
    print(f"  ground_truth: {g}")
print("=============\n")

# Guard — skip rows with empty contexts or ground_truth
filtered = [
    (q, a, c, g)
    for q, a, c, g in zip(questions, answers, contexts, ground_truths)
    if c and any(t.strip() for t in c) and g.strip()
]

if not filtered:
    print("ERROR: No valid rows to evaluate. Check contexts and ground_truth fields.")
    exit(1)

f_questions, f_answers, f_contexts, f_ground_truths = zip(*filtered)
print(f"Evaluating {len(f_questions)} valid row(s) out of {len(questions)} total.\n")

eval_dataset = Dataset.from_dict({
    "question":     list(f_questions),
    "answer":       list(f_answers),
    "contexts":     list(f_contexts),
    "ground_truth": list(f_ground_truths),
})

# Evaluate — pass both llm and embeddings to avoid any fallback issues
result = evaluate(
    eval_dataset,
    metrics=[faithfulness, context_precision, answer_relevancy],
    llm=llm,
    embeddings=embeddings
)

print(result.to_pandas().to_json())  # structured output for superpowers tool