import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', redirectTo: 'query', pathMatch: 'full' },
  {
    path: 'query',
    loadComponent: () =>
      import('./features/query/query.component').then(m => m.QueryComponent)
  },
  {
    path: 'ingest',
    loadComponent: () =>
      import('./features/ingest/ingest.component').then(m => m.IngestComponent)
  },
  {
    path: 'documents',
    loadComponent: () =>
      import('./features/documents/documents.component').then(m => m.DocumentsComponent)
  }
];
