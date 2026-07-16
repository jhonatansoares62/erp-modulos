package br.com.erpkit.backup.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cifra AES-256-GCM: round-trip, deteccao de adulteracao (tag GCM) e chave errada.
 * Portado do backup in-JVM do ERP — garante que a extracao preservou a seguranca em repouso.
 */
class BackupCriptoTest {

    @Test
    void cifraEDecifraRoundTrip(@TempDir Path tmp) throws IOException {
        BackupCripto cripto = new BackupCripto(tmp.resolve("backup.properties").toString());
        File plano = tmp.resolve("dump.bin").toFile();
        byte[] conteudo = "conteudo secreto do backup cao 12345".getBytes();
        Files.write(plano.toPath(), conteudo);

        File cifrado = tmp.resolve("dump.bin.enc").toFile();
        cripto.cifrarArquivo(plano, cifrado);
        assertTrue(cifrado.length() > 0);
        assertFalse(Arrays.equals(conteudo, Files.readAllBytes(cifrado.toPath())), "o .enc nao pode ser o texto em claro");

        File saida = tmp.resolve("dump.out").toFile();
        cripto.decifrarArquivo(cifrado, saida);
        assertArrayEquals(conteudo, Files.readAllBytes(saida.toPath()));
    }

    @Test
    void decifrarDetectaAdulteracao(@TempDir Path tmp) throws IOException {
        BackupCripto cripto = new BackupCripto(tmp.resolve("backup.properties").toString());
        File plano = tmp.resolve("d.bin").toFile();
        Files.write(plano.toPath(), "abcdefghijklmnop".getBytes());
        File cifrado = tmp.resolve("d.enc").toFile();
        cripto.cifrarArquivo(plano, cifrado);

        // Vira 1 bit do ultimo byte (tag GCM) — a verificacao no doFinal deve falhar.
        byte[] bytes = Files.readAllBytes(cifrado.toPath());
        bytes[bytes.length - 1] ^= 0x01;
        Files.write(cifrado.toPath(), bytes);

        File saida = tmp.resolve("d.out").toFile();
        assertThrows(IOException.class, () -> cripto.decifrarArquivo(cifrado, saida));
    }

    @Test
    void decifrarComChaveErradaFalha(@TempDir Path tmp) throws IOException {
        BackupCripto c1 = new BackupCripto(tmp.resolve("k1.properties").toString());
        File plano = tmp.resolve("p.bin").toFile();
        Files.write(plano.toPath(), "segredo".getBytes());
        File cifrado = tmp.resolve("p.enc").toFile();
        c1.cifrarArquivo(plano, cifrado);

        // Outra instancia com outro arquivo de chave = chave diferente (auto-gerada).
        BackupCripto c2 = new BackupCripto(tmp.resolve("k2.properties").toString());
        File saida = tmp.resolve("p.out").toFile();
        assertThrows(IOException.class, () -> c2.decifrarArquivo(cifrado, saida));
    }
}
