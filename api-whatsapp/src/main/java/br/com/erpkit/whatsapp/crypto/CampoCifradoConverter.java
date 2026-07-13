package br.com.erpkit.whatsapp.crypto;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Cifra/decifra um campo String em repouso via {@link CampoCripto} (AES-256-GCM).
 *
 * <p>Aplicado explicitamente com {@code @Convert(converter = CampoCifradoConverter.class)}
 * nos campos secretos — NAO {@code autoApply}. Instanciado pelo proprio JPA (construtor
 * vazio); toda a logica de chave/cripto e estatica em {@link CampoCripto}, portanto o
 * converter nao precisa de injecao Spring nem de ordem de boot.
 *
 * <p>Leitura tolerante: valor sem prefixo {@code v1:} e devolvido como texto plano legado
 * (migracao sem downtime); falha de decifragem devolve {@code null} sem lancar.
 */
@Converter
public class CampoCifradoConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String atributo) {
        return CampoCripto.cifrar(atributo);
    }

    @Override
    public String convertToEntityAttribute(String coluna) {
        return CampoCripto.decifrar(coluna);
    }
}
