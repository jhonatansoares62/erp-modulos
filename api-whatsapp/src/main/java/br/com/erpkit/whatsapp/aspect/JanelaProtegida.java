package br.com.erpkit.whatsapp.aspect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marker para metodos do {@code WhatsAppCloudClient} que devem ter a janela 24h
 * verificada pelo {@link JanelaEnforcementAspect} antes de qualquer chamada
 * Cloud API (D-03 do CONTEXT.md + OUT-07 do REQUIREMENTS.md).
 *
 * <p><b>Convencao posicional (D-03):</b> o metodo anotado DEVE ter
 * {@code String telefone} como primeiro argumento. O aspect le {@code args[0]} e
 * lanca {@link IllegalStateException} fail-fast em runtime se a convencao nao
 * for honrada. Sem atributos pois 100% dos metodos publicos de
 * {@code WhatsAppCloudClient} (4 envios per OUT-01..04) tem telefone como
 * primeiro arg.
 *
 * <p><b>Por que annotation marker e nao pointcut por nome
 * ({@code execution(* enviar*(..))}):</b> annotation forca declaracao explicita
 * em cada metodo — qualquer novo {@code enviar*} adicionado em Phase 5+ deve
 * decidir conscientemente: ou anota e entra no enforcement, ou nao anota e
 * burla. Pointcut por convencao silencia esta decisao.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface JanelaProtegida {
}
