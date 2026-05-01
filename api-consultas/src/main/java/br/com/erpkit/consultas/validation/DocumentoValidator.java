package br.com.erpkit.consultas.validation;

public final class DocumentoValidator {

    private DocumentoValidator() {}

    public static String normalizarCep(String cep) {
        if (cep == null) return "";
        return cep.replaceAll("\\D", "");
    }

    public static String normalizarCnpj(String cnpj) {
        if (cnpj == null) return "";
        return cnpj.replaceAll("\\D", "");
    }

    public static boolean cepValido(String cepNumerico) {
        return cepNumerico != null && cepNumerico.matches("\\d{8}");
    }

    public static boolean cnpjValido(String cnpjNumerico) {
        if (cnpjNumerico == null || !cnpjNumerico.matches("\\d{14}")) return false;
        if (cnpjNumerico.chars().distinct().count() == 1) return false;
        int dv1 = calcularDigito(cnpjNumerico, 12, new int[]{5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        if (dv1 != Character.getNumericValue(cnpjNumerico.charAt(12))) return false;
        int dv2 = calcularDigito(cnpjNumerico, 13, new int[]{6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2});
        return dv2 == Character.getNumericValue(cnpjNumerico.charAt(13));
    }

    private static int calcularDigito(String s, int len, int[] pesos) {
        int soma = 0;
        for (int i = 0; i < len; i++) {
            soma += Character.getNumericValue(s.charAt(i)) * pesos[i];
        }
        int resto = soma % 11;
        return resto < 2 ? 0 : 11 - resto;
    }
}
