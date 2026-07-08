export interface CatalogoDimensao {
  chave: string;        // chave que o emissor põe no contexto do evento
  rotulo: string;       // rótulo amigável no dialog
  cadastroRef: string;  // tipo do endpoint /api/cadastros/{tipo}
}

export interface CatalogoEvento {
  codigo: string;
  rotulo: string;
  descricao: string;
  icone: string;
  dimensoes: CatalogoDimensao[];
}

export const CATALOGO_EVENTOS: CatalogoEvento[] = [
  {
    codigo: 'venda.finalizada',
    rotulo: 'Venda finalizada',
    descricao: 'OS concluída — gera conta a receber (competência da receita).',
    icone: 'pi pi-shopping-cart',
    dimensoes: [],
  },
  {
    codigo: 'recebimento.baixado',
    rotulo: 'Recebimento',
    descricao: 'Parcela paga pelo cliente — baixa o a receber.',
    icone: 'pi pi-wallet',
    dimensoes: [
      { chave: 'meioPagamento', rotulo: 'Meio de pagamento', cadastroRef: 'formas-pagamento' },
    ],
  },
];
