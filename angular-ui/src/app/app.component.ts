import { Component } from '@angular/core';
import { RouterOutlet, RouterLink, RouterLinkActive } from '@angular/router';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatListModule } from '@angular/material/list';
import { MatIconModule } from '@angular/material/icon';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatListModule, MatIconModule],
  template: `
    <mat-sidenav-container class="container">
      <mat-sidenav mode="side" opened class="sidenav">
        <div class="sidenav-title">RAG System</div>
        <mat-nav-list>
          <a mat-list-item routerLink="/query" routerLinkActive="active">
            <mat-icon matListItemIcon>chat</mat-icon>
            <span matListItemTitle>Query</span>
          </a>
          <a mat-list-item routerLink="/ingest" routerLinkActive="active">
            <mat-icon matListItemIcon>upload_file</mat-icon>
            <span matListItemTitle>Ingest</span>
          </a>
          <a mat-list-item routerLink="/documents" routerLinkActive="active">
            <mat-icon matListItemIcon>folder</mat-icon>
            <span matListItemTitle>Documents</span>
          </a>
        </mat-nav-list>
      </mat-sidenav>
      <mat-sidenav-content class="content">
        <router-outlet />
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: [`
    .container { height: 100vh; }
    .sidenav { width: 220px; border-right: 1px solid rgba(0,0,0,0.12); }
    .sidenav-title { padding: 20px 16px 12px; font-size: 1rem; font-weight: 500;
                     border-bottom: 1px solid rgba(0,0,0,0.12); margin-bottom: 8px; }
    .content { padding: 24px; height: 100%; box-sizing: border-box; }
    .active { background: rgba(63,81,181,0.1); }
  `]
})
export class AppComponent {}
