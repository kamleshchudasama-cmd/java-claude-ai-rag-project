import { TestBed, ComponentFixture } from '@angular/core/testing';
import { ConfirmDialogComponent } from './confirm-dialog.component';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';

describe('ConfirmDialogComponent', () => {
  let fixture: ComponentFixture<ConfirmDialogComponent>;
  let component: ConfirmDialogComponent;
  let dialogRefSpy: jasmine.SpyObj<MatDialogRef<ConfirmDialogComponent>>;

  beforeEach(async () => {
    dialogRefSpy = jasmine.createSpyObj('MatDialogRef', ['close']);

    await TestBed.configureTestingModule({
      imports: [ConfirmDialogComponent, NoopAnimationsModule],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { filename: 'test.pdf' } },
        { provide: MatDialogRef, useValue: dialogRefSpy }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(ConfirmDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the component', () => {
    expect(component).toBeTruthy();
  });

  it('renders the filename from injected dialog data', () => {
    const content: HTMLElement = fixture.nativeElement.querySelector('mat-dialog-content');
    expect(content.textContent).toContain('test.pdf');
  });

  it('Cancel button closes the dialog without confirming when clicked', () => {
    const buttons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('button')
    );
    const cancelBtn = buttons.find(b => b.textContent?.trim() === 'Cancel');
    expect(cancelBtn).toBeTruthy();
    cancelBtn!.click();
    expect(dialogRefSpy.close).toHaveBeenCalledTimes(1);
    const arg = dialogRefSpy.close.calls.mostRecent().args[0];
    expect(arg).toBeFalsy();
  });

  it('Delete button closes the dialog with true when clicked', () => {
    const buttons: HTMLButtonElement[] = Array.from(
      fixture.nativeElement.querySelectorAll('button')
    );
    const deleteBtn = buttons.find(b => b.textContent?.trim() === 'Delete');
    expect(deleteBtn).toBeTruthy();
    deleteBtn!.click();
    expect(dialogRefSpy.close).toHaveBeenCalledWith(true);
  });
});
