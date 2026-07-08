import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { ButtonModule } from 'primeng/button';
import { Tooltip } from 'primeng/tooltip';

export type AcaoTipo = 'ver' | 'editar' | 'excluir' | 'custom';

// Botao de acao para linhas de grid: icone-only, ghost, size small.
// acao define icone/severity/tooltip padrao; 'editar' fica oculto no mobile.
@Component({
  selector: 'app-botao-acao',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ButtonModule, Tooltip],
  template: `
    <p-button
      [icon]="iconeFinal()"
      [text]="true"
      size="small"
      [severity]="severityFinal()"
      [styleClass]="styleClassFinal()"
      [disabled]="disabled()"
      [loading]="loading()"
      [pTooltip]="tooltipFinal()"
      tooltipPosition="top"
      [ariaLabel]="ariaLabelFinal()"
      (onClick)="clicado.emit($event)"
    />
  `,
})
export class BotaoAcaoComponent {
  acao = input<AcaoTipo>('custom');
  icon = input<string>();
  severity = input<'success' | 'info' | 'warn' | 'danger' | 'secondary' | 'contrast'>();
  tooltip = input<string>();
  ariaLabel = input<string>();
  disabled = input(false);
  loading = input(false);

  clicado = output<MouseEvent>();

  iconeFinal = computed(() => {
    switch (this.acao()) {
      case 'ver': return 'pi pi-eye';
      case 'editar': return 'pi pi-pencil';
      case 'excluir': return 'pi pi-trash';
      default: return this.icon() ?? '';
    }
  });

  severityFinal = computed(() =>
    this.acao() === 'excluir' ? 'danger' : this.severity()
  );

  styleClassFinal = computed(() =>
    this.acao() === 'editar' ? 'rt-acao-editar-desktop' : ''
  );

  tooltipFinal = computed(() => {
    if (this.tooltip()) return this.tooltip();
    switch (this.acao()) {
      case 'ver': return 'Ver';
      case 'editar': return 'Editar';
      case 'excluir': return 'Excluir';
      default: return undefined;
    }
  });

  ariaLabelFinal = computed(() => this.ariaLabel() ?? this.tooltipFinal());
}
