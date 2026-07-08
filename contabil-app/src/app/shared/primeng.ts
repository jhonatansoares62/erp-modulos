// Barrel re-export dos módulos PrimeNG usados no app (espelha o do Odonto, sem os
// módulos que puxam deps extras não instaladas aqui — ex.: UIChart/chart.js).
import { BadgeModule } from 'primeng/badge';
import { ButtonModule } from 'primeng/button';
import { CardModule } from 'primeng/card';
import { CheckboxModule } from 'primeng/checkbox';
import { ColorPickerModule } from 'primeng/colorpicker';
import { ConfirmDialog } from 'primeng/confirmdialog';
import { DataView } from 'primeng/dataview';
import { DatePicker } from 'primeng/datepicker';
import { DatePickerModule } from 'primeng/datepicker';
import { Dialog } from 'primeng/dialog';
import { DialogModule } from 'primeng/dialog';
import { FileUploadModule } from 'primeng/fileupload';
import { IconFieldModule } from 'primeng/iconfield';
import { InputIconModule } from 'primeng/inputicon';
import { InputMaskModule } from 'primeng/inputmask';
import { InputNumberModule } from 'primeng/inputnumber';
import { InputTextModule } from 'primeng/inputtext';
import { MessageModule } from 'primeng/message';
import { MultiSelectModule } from 'primeng/multiselect';
import { PanelMenu } from 'primeng/panelmenu';
import { PasswordModule } from 'primeng/password';
import { ProgressBar } from 'primeng/progressbar';
import { ProgressSpinner } from 'primeng/progressspinner';
import { RadioButtonModule } from 'primeng/radiobutton';
import { SelectModule } from 'primeng/select';
import { SelectButton } from 'primeng/selectbutton';
import { Skeleton } from 'primeng/skeleton';
import { SplitButtonModule } from 'primeng/splitbutton';
import { TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { TagModule } from 'primeng/tag';
import { TextareaModule } from 'primeng/textarea';
import { Timeline } from 'primeng/timeline';
import { Toast } from 'primeng/toast';
import { ToastModule } from 'primeng/toast';
import { ToggleSwitchModule } from 'primeng/toggleswitch';
import { Toolbar } from 'primeng/toolbar';
import { Tooltip } from 'primeng/tooltip';
import { TooltipModule } from 'primeng/tooltip';
import { Tree } from 'primeng/tree';

export const PRIMENG_MODULES = [
  BadgeModule, ButtonModule, CardModule, CheckboxModule,
  ColorPickerModule, ConfirmDialog, DataView, DatePicker, DatePickerModule,
  Dialog, DialogModule, FileUploadModule, IconFieldModule, InputIconModule,
  InputMaskModule, InputNumberModule, InputTextModule, MessageModule,
  MultiSelectModule, PanelMenu, PasswordModule, ProgressBar, ProgressSpinner,
  RadioButtonModule, SelectModule, SelectButton, Skeleton, SplitButtonModule, TableModule,
  TabsModule, TagModule, TextareaModule, Timeline, Toast, ToastModule,
  ToggleSwitchModule, Toolbar, Tooltip, TooltipModule, Tree
] as const;
