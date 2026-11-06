import { ChangeDetectionStrategy, ChangeDetectorRef, Component, DestroyRef, Input, OnChanges, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { filter } from 'rxjs/operators';
import { PerfilService } from '../../services/perfil.service';

/** Foto de perfil do usuário, ou a inicial do nome num círculo laranja enquanto não carrega / se não houver foto. */
@Component({
  selector: 'app-avatar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="avatar" [style.width.px]="tamanho" [style.height.px]="tamanho"
          [style.font-size.px]="tamanho * 0.45" [attr.title]="titulo ? usuario : null">
      @if (url) {
        <img [src]="url" [alt]="'Foto de ' + usuario" />
      } @else {
        {{ inicial }}
      }
    </span>
  `,
  styles: [`
    :host { display: inline-flex; flex-shrink: 0; }
    .avatar {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      border-radius: 50%;
      overflow: hidden;
      background: var(--avatar-fundo, var(--accent, #ff6a13));
      color: var(--avatar-texto, #fff);
      font-family: var(--font-display, sans-serif);
      font-weight: 700;
      line-height: 1;
      user-select: none;
    }
    img { width: 100%; height: 100%; object-fit: cover; display: block; }
  `]
})
export class Avatar implements OnChanges {
  @Input({ required: true }) usuario = '';
  @Input() tamanho = 32;
  /** Mostra o nome ao passar o mouse (útil nas listas, onde o nome não aparece ao lado). */
  @Input() titulo = false;

  url: string | null = null;

  private perfil = inject(PerfilService);
  private cdr = inject(ChangeDetectorRef);
  private assinatura?: Subscription;

  constructor() {
    this.perfil.fotoMudou$.pipe(
      filter(u => !!u && u === this.usuario?.toUpperCase()),
      takeUntilDestroyed(inject(DestroyRef))
    ).subscribe(() => this.carregar());
    inject(DestroyRef).onDestroy(() => this.assinatura?.unsubscribe());
  }

  get inicial(): string {
    return (this.usuario || '?').trim().charAt(0).toUpperCase();
  }

  ngOnChanges(): void {
    this.carregar();
  }

  private carregar(): void {
    this.assinatura?.unsubscribe();
    this.url = null;
    this.cdr.markForCheck();
    if (!this.usuario) return;
    this.assinatura = this.perfil.foto(this.usuario).subscribe(url => {
      this.url = url;
      this.cdr.markForCheck();
    });
  }
}
