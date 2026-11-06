import { ChangeDetectorRef, Component, OnInit, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AuthService } from './services/auth.service';
import { Router } from '@angular/router';
import { AmbienteDTO, PerfilService } from './services/perfil.service';


@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet],
  templateUrl: './app.html',
  styleUrls: ['./app.scss']
})
export class AppComponent implements OnInit {
  title = 'planning-poker';
  ambiente: AmbienteDTO | null = null;

  private perfil = inject(PerfilService);
  private cdr = inject(ChangeDetectorRef);

  constructor(public auth: AuthService, private router: Router) {}

  ngOnInit(): void {
    this.perfil.ambiente().subscribe(ambiente => {
      this.ambiente = ambiente;
      this.cdr.detectChanges();
    });
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }
}
