import { definePreset } from '@primeuix/themes';
import Lara from '@primeuix/themes/lara';

// WhatsApp — paleta verde (emerald) para diferenciar visualmente do app contábil (cobre),
// mantendo o mesmo esqueleto/tema ErpKit (Lara + surfaces slate).
const emeraldPalette = {
  50: '#ecfdf5', 100: '#d1fae5', 200: '#a7f3d0', 300: '#6ee7b7',
  400: '#34d399', 500: '#10b981', 600: '#059669', 700: '#047857',
  800: '#065f46', 900: '#064e3b', 950: '#022c22',
};

export const ErpKitPreset = definePreset(Lara, {
  semantic: {
    focusRing: { width: '2px', style: 'solid', color: '{primary.500}', offset: '2px', shadow: 'none' },
    primary: emeraldPalette,
    colorScheme: {
      light: {
        surface: {
          0: '#ffffff',
          50: '{slate.50}', 100: '{slate.100}', 200: '{slate.200}',
          300: '{slate.300}', 400: '{slate.400}', 500: '{slate.500}',
          600: '{slate.600}', 700: '{slate.700}', 800: '{slate.800}',
          900: '{slate.900}', 950: '{slate.950}'
        }
      },
      dark: {
        surface: {
          0: '#ffffff', 50: '#e6edf3', 100: '#c0cdd8', 200: '#8b9eb0',
          300: '#6b8099', 400: '#4d6275', 500: '#3a4d5e', 600: '#2d3d4d',
          700: '#242a33', 800: '#1c2128', 900: '#161b22', 950: '#0f1419'
        },
        primary: {
          color: '{primary.400}', contrastColor: '{surface.950}',
          hoverColor: '{primary.300}', activeColor: '{primary.200}'
        }
      }
    }
  },
  components: {
    card: { root: { borderRadius: '12px', shadow: '0 1px 3px rgba(0,0,0,0.06), 0 1px 2px rgba(0,0,0,0.04)' } },
    datatable: {
      headerCell: { padding: '0.3rem 0.75rem' },
      bodyCell: { padding: '0.5rem 0.75rem' },
      row: { stripedBackground: '{surface.50}' }
    },
    tag: { root: { fontSize: '0.6875rem', fontWeight: '600', padding: '3px 10px', borderRadius: '999px' } },
    button: { root: { borderRadius: '8px' } }
  }
});
