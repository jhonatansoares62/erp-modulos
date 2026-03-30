package br.com.erpkit.storage.dto;

import java.util.Map;

public class StorageEstatisticasResponse {

    private long totalArquivos;
    private long totalBytes;
    private String totalFormatado;
    private Map<String, CategoriaStats> porCategoria;

    public static class CategoriaStats {
        private long quantidade;
        private long bytes;
        private String formatado;

        public CategoriaStats() {
        }

        public CategoriaStats(long quantidade, long bytes, String formatado) {
            this.quantidade = quantidade;
            this.bytes = bytes;
            this.formatado = formatado;
        }

        public long getQuantidade() {
            return quantidade;
        }

        public void setQuantidade(long quantidade) {
            this.quantidade = quantidade;
        }

        public long getBytes() {
            return bytes;
        }

        public void setBytes(long bytes) {
            this.bytes = bytes;
        }

        public String getFormatado() {
            return formatado;
        }

        public void setFormatado(String formatado) {
            this.formatado = formatado;
        }
    }

    // Getters e Setters

    public long getTotalArquivos() {
        return totalArquivos;
    }

    public void setTotalArquivos(long totalArquivos) {
        this.totalArquivos = totalArquivos;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public String getTotalFormatado() {
        return totalFormatado;
    }

    public void setTotalFormatado(String totalFormatado) {
        this.totalFormatado = totalFormatado;
    }

    public Map<String, CategoriaStats> getPorCategoria() {
        return porCategoria;
    }

    public void setPorCategoria(Map<String, CategoriaStats> porCategoria) {
        this.porCategoria = porCategoria;
    }
}
