package br.com.erpkit.whatsapp.controller;

import br.com.erpkit.shared.exception.ModuloException;
import br.com.erpkit.whatsapp.config.AuthProperties;
import br.com.erpkit.whatsapp.dto.LoginRequest;
import br.com.erpkit.whatsapp.dto.LoginResponse;
import br.com.erpkit.whatsapp.dto.MeResponse;
import br.com.erpkit.whatsapp.model.Atendente;
import br.com.erpkit.whatsapp.security.JwtService;
import br.com.erpkit.whatsapp.security.WhatsappAuthFilter;
import br.com.erpkit.whatsapp.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Auth propria do atendente (app WhatsApp standalone). Dois endpoints sob
 * {@code /api/whatsapp/auth}: {@code /login} (publico — emite JWT) e {@code /me}
 * (Bearer — quem sou eu). Nao afeta o X-API-Key (ERP) nem o webhook (HMAC).
 */
@RestController
@RequestMapping("/api/whatsapp/auth")
public class AuthController {

    private final AuthService authService;
    private final JwtService jwtService;
    private final AuthProperties props;

    public AuthController(AuthService authService, JwtService jwtService, AuthProperties props) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.props = props;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        Atendente a = authService.autenticar(req.getEmail(), req.getSenha());
        String token = jwtService.gerar(a.getEmail(), a.getNome());
        return ResponseEntity.ok(new LoginResponse(token, a.getEmail(), a.getNome(), props.getEmpresaNome()));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(
            @RequestAttribute(name = WhatsappAuthFilter.ATTR_EMAIL, required = false) String email) {
        if (email == null) {
            throw new ModuloException("Nao autenticado", HttpStatus.UNAUTHORIZED);
        }
        Atendente a = authService.buscarPorEmail(email);
        return ResponseEntity.ok(new MeResponse(a.getEmail(), a.getNome(), props.getEmpresaNome()));
    }
}
