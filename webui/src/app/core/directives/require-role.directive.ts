import { Directive, Input, OnDestroy, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { Subscription } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Structural directive: render the attached template only when the current
 * user's role satisfies the given threshold (role number <= minRole, to
 * match the backend's lower-is-higher convention).
 *
 * Usage:
 * <pre>
 *   &lt;div *appRequireRole="AccountRoleType.EVENT_ADMIN"&gt;
 *     admin-only content
 *   &lt;/div&gt;
 * </pre>
 *
 * This is UX, not security: the backend filter is the actual enforcement.
 * The directive exists so referees don't see menu links that would just
 * bounce them back to home.
 */
@Directive({
  selector: '[appRequireRole]',
  standalone: true,
})
export class RequireRoleDirective implements OnInit, OnDestroy {
  private minRole: number | null = null;
  private sub?: Subscription;
  private rendered = false;

  @Input() set appRequireRole(value: number) {
    this.minRole = value;
    // If Angular re-sets the input later (ngIf-style dynamic thresholds),
    // re-evaluate against the current role snapshot.
    if (this.sub) this.evaluate();
  }

  constructor(
    private tpl: TemplateRef<unknown>,
    private vcr: ViewContainerRef,
    private auth: AuthService,
  ) {}

  ngOnInit(): void {
    this.sub = this.auth.role$().subscribe(() => this.evaluate());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private evaluate(): void {
    const role = this.auth.currentRole();
    const allowed = this.minRole !== null && role !== null && role <= this.minRole;
    if (allowed && !this.rendered) {
      this.vcr.createEmbeddedView(this.tpl);
      this.rendered = true;
    } else if (!allowed && this.rendered) {
      this.vcr.clear();
      this.rendered = false;
    }
  }
}
