import { Component, Input } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { MatExpansionModule } from '@angular/material/expansion';
import { Citation } from '../../core/models';

@Component({
  selector: 'app-citation-card',
  standalone: true,
  imports: [MatExpansionModule, DecimalPipe],
  template: `
    <mat-expansion-panel>
      <mat-expansion-panel-header>
        <mat-panel-title>
          [{{ citation.ref }}] {{ citation.filename }} &middot; {{ citation.score | number:'1.2-2' }}
        </mat-panel-title>
      </mat-expansion-panel-header>
      <p class="chunk-text">{{ citation.chunkText }}</p>
    </mat-expansion-panel>
  `,
  styles: ['.chunk-text { font-size: 0.85rem; color: rgba(0,0,0,0.6); white-space: pre-wrap; }']
})
export class CitationCardComponent {
  @Input({ required: true }) citation!: Citation;
}
