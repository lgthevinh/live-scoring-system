import { Component, OnInit, signal, inject } from '@angular/core';
import {AccountRoleType} from "../../../core/define/AccounRoleType";
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {AuthService} from '../../../core/services/auth.service';
import {ToastService} from '../../../core/services/toast.service';

interface Account {
  username: string;
  role: number;
}

@Component({
  selector: 'app-create-account',
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './create-account.html',
  styleUrl: './create-account.css'
})
export class CreateAccount implements OnInit {
  private authService = inject(AuthService);
  private toastService = inject(ToastService);
  private formBuilder = inject(FormBuilder);

  protected readonly AccountRoleType = AccountRoleType;
  protected passwordVisible: boolean = false;

  accounts: Account[] = [];
  isEditing: boolean = false;
  editingUsername: string = '';
  isLoading: boolean = false;

  createAccountForm: FormGroup = this.formBuilder.group({
    username: ['', Validators.required],
    password: ['', Validators.required],
    reEnterPassword: ['', Validators.required],
    role: [0, Validators.required]
  });

  ngOnInit() {
    this.loadAccounts();
  }

  loadAccounts() {
    this.isLoading = true;
    this.authService.getAllAccounts().subscribe({
      next: (response: any) => {
        this.accounts = response.accounts || [];
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error loading accounts:', err);
        this.toastService.show('Error loading accounts', 'error');
        this.isLoading = false;
      }
    });
  }

  togglePasswordVisibility() {
    this.passwordVisible = !this.passwordVisible;
  }

  onSubmit() {
    const formValues = this.createAccountForm.value;
    if (this.isEditing) {
      this.handleUpdate(formValues);
    } else {
      this.handleCreate(formValues);
    }
  }

  handleCreate(formValues: any) {
    if (this.validatePasswords(formValues.password, formValues.reEnterPassword)) {
      let credentials = {
        username: formValues.username,
        password: formValues.password,
        role: formValues.role
      }
      this.isLoading = true;
      this.authService.createAccount(credentials).subscribe({
        next: () => {
          console.log('Account created successfully.');
          this.toastService.show('Account created successfully', 'success');
          this.resetForm();
          this.loadAccounts();
        },
        error: (err) => {
          this.toastService.show('Error creating account: ' + (err.error?.message || 'Unknown error'), 'error');
          console.error('Error creating account:', err);
          this.isLoading = false;
        }
      });
    } else {
      this.toastService.show('Passwords do not match', 'error');
    }
  }

  handleUpdate(formValues: any) {
    if (this.validatePasswords(formValues.password, formValues.reEnterPassword)) {
      let credentials = {
        password: formValues.password,
        role: formValues.role
      }
      this.isLoading = true;
      this.authService.updateAccount(this.editingUsername, credentials).subscribe({
        next: () => {
          console.log('Account updated successfully.');
          this.toastService.show('Account updated successfully', 'success');
          this.resetForm();
          this.loadAccounts();
        },
        error: (err) => {
          this.toastService.show('Error updating account: ' + (err.error?.message || 'Unknown error'), 'error');
          console.error('Error updating account:', err);
          this.isLoading = false;
        }
      });
    } else {
      this.toastService.show('Passwords do not match', 'error');
    }
  }

  editAccount(account: Account) {
    this.isEditing = true;
    this.editingUsername = account.username;
    this.createAccountForm.patchValue({
      username: account.username,
      password: '',
      reEnterPassword: '',
      role: account.role
    });
    // Username field should be disabled in edit mode
    this.createAccountForm.get('username')?.disable();
  }

  deleteAccount(username: string) {
    // Store account for potential undo
    const deletedAccount = this.accounts.find(a => a.username === username);

    this.isLoading = true;
    this.authService.deleteAccount(username).subscribe({
      next: () => {
        this.toastService.show(`Account "${username}" deleted`, 'success', 15000, {
          label: 'Undo',
          onAction: () => {
            if (deletedAccount) {
              // Generate a secure random temporary password
              const tempPassword = this.generateTempPassword();
              const credentials = {
                username: deletedAccount.username,
                password: tempPassword,
                role: deletedAccount.role
              };
              this.authService.createAccount(credentials).subscribe(() => {
                this.loadAccounts();
                this.toastService.show(`Account restored. Temporary password: ${tempPassword} - Please reset password immediately.`, 'success', 10000);
              }, () => {
                this.toastService.show('Failed to restore account', 'error');
              });
            }
          }
        });
        this.loadAccounts();
      },
      error: (err) => {
        this.toastService.show('Error deleting account: ' + (err.error?.message || 'Unknown error'), 'error');
        console.error('Error deleting account:', err);
        this.isLoading = false;
      }
    });
  }

  resetForm() {
    this.isEditing = false;
    this.editingUsername = '';
    this.createAccountForm.reset();
    this.createAccountForm.get('username')?.enable();
  }

  validatePasswords(password: string, confirmPassword: string): boolean {
    return password === confirmPassword;
  }

  /**
   * Generate a secure random temporary password for account restoration
   */
  private generateTempPassword(): string {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*';
    const length = 12;
    let password = '';
    for (let i = 0; i < length; i++) {
      password += chars.charAt(Math.floor(Math.random() * chars.length));
    }
    return password;
  }

  getRoleName(role: number): string {
    switch (role) {
      case AccountRoleType.EVENT_ADMIN: return 'Event Admin';
      case AccountRoleType.SCOREKEEPER: return 'Scorekeeper';
      case AccountRoleType.HEAD_REFEREE: return 'Head-referee';
      case AccountRoleType.SCORING_REFEREE: return 'Scoring-referee';
      case AccountRoleType.EMCEE: return 'Emcee';
      default: return 'Unknown';
    }
  }
}
