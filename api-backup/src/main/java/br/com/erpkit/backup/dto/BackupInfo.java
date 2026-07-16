package br.com.erpkit.backup.dto;

import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class BackupInfo {

    private String nome;
    private long tamanho;
    private String tipo;
    private LocalDateTime data;

    public BackupInfo() {
    }

    public BackupInfo(File file) {
        this.nome = file.getName();
        this.tamanho = file.length();
        String nomeArquivo = file.getName();
        this.tipo = nomeArquivo.endsWith(".enc") ? "cifrado"
                : (nomeArquivo.endsWith(".dump") ? "dump" : "sql");
        this.data = LocalDateTime.ofInstant(
                Instant.ofEpochMilli(file.lastModified()),
                ZoneId.systemDefault()
        );
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public long getTamanho() {
        return tamanho;
    }

    public void setTamanho(long tamanho) {
        this.tamanho = tamanho;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public LocalDateTime getData() {
        return data;
    }

    public void setData(LocalDateTime data) {
        this.data = data;
    }
}
