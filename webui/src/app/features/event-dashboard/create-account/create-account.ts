import { Component, OnInit, signal } from '@angular/core';
import {AccountRoleType} from "../../../core/define/AccounRoleType";
import {FormBuilder, FormGroup, ReactiveFormsModule, Validators} from '@angular/forms';
import {CommonModule} from '@angular/common';
import {AuthService} from '../../../core/services/auth.service';

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
  protected readonly AccountRoleType = AccountRoleType;
  protected passwordVisible: boolean = false;

  accounts: Account[] = [];
  isEditing: boolean = false;
  editingUsername: string = '';
  isLoading: boolean = false;
  message: string = '';
  messageType: string = '';

  createAccountForm: FormGroup;

  constructor(
    private formBuilder: FormBuilder,
    private authService: AuthService
  ) {
    this.createAccountForm = this.formBuilder.group({
      username: ['', Validators.required],
      password: ['', Validators.required],
      reEnterPassword: ['', Validators.required],
      role: [0, Validators.required]
    });
  }

  ngOnInit() {
    this.loadAccounts();
  }

  loadAccounts() {
    this.isLoading = true;
    this.message = '';
    this.authService.getAllAccounts().subscribe({
      next: (response: any) => {
        this.accounts = response.accounts || [];
        this.isLoading = false;
      },
      error: (err) => {
        console.error('Error loading accounts:', err);
        this.showMessage('Error loading accounts', 'error');
        this.isLoading = false;
      }
    });
  }

  showMessage(text: string, type: string) {
    this.message = text;
    this.messageType = type;
    // Auto-clear message after 3 seconds
    setTimeout(() => {
      this.message = '';
      this.messageType = '';
    }, 3000);
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
          this.showMessage('Account created successfully', 'success');
          this.resetForm();
          this.loadAccounts();
        },
        error: (err) => {
          this.showMessage('Error creating account: ' + (err.error?.message || 'Unknown error'), 'error');
          console.error('Error creating account:', err);
          this.isLoading = false;
        }
      });
    } else {
      this.showMessage('Passwords do not match', 'error');
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
          this.showMessage('Account updated successfully', 'success');
          this.resetForm();
          this.loadAccounts();
        },
        error: (err) => {
          this.showMessage('Error updating account: ' + (err.error?.message || 'Unknown error'), 'error');
          console.error('Error updating account:', err);
          this.isLoading = false;
        }
      });
    } else {
      this.showMessage('Passwords do not match', 'error');
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
    if (confirm(`Are you sure you want to delete account "${username}"?`)) {
      this.isLoading = true;
      this.authService.deleteAccount(username).subscribe({
        next: () => {
          this.showMessage('Account deleted successfully', 'success');
          this.loadAccounts();
        },
        error: (err) => {
          this.showMessage('Error deleting account: ' + (err.error?.message || 'Unknown error'), 'error');
          console.error('Error deleting account:', err);
          this.isLoading = false;
        }
      });
    }
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
