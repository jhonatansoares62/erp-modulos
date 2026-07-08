import { definePreset } from '@primeuix/themes';
import Lara from '@primeuix/themes/lara';

const copperPalette = {
  50: '#fdf6f0', 100: '#f9e8d8', 200: '#f2ccad', 300: '#e8ab7c',
  400: '#E08B50', 500: '#C0703A', 600: '#a65e2f', 700: '#8a4e27',
  800: '#6e3e1f', 900: '#523017', 950: '#3a2110',
};

export const ErpKitPreset = definePreset(Lara, {
  semantic: {
    focusRing: { width: '2px', style: 'solid', color: '{primary.500}', offset: '2px', shadow: 'none' },
    primary: copperPalette,
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
