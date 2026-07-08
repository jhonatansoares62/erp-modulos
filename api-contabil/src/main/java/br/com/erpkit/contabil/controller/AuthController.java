package br.com.erpkit.contabil.controller;

import br.com.erpkit.contabil.config.AuthProperties;
import br.com.erpkit.contabil.dto.LoginRequest;
import br.com.erpkit.contabil.dto.LoginResponse;
import br.com.erpkit.contabil.dto.MeResponse;
import br.com.erpkit.contabil.model.Contador;
import br.com.erpkit.contabil.security.ContabilAuthFilter;
import br.com.erpkit.contabil.security.JwtService;
import br.com.erpkit.contabil.service.AuthService;
import br.com.erpkit.shared.exception.ModuloException;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/auth")
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
        Contador c = authService.autenticar(req.getEmail(), req.getSenha());
        String token = jwtService.gerar(c.getEmail(), c.getNome());
        return ResponseEntity.ok(new LoginResponse(token, c.getEmail(), c.getNome(), props.getEmpresaNome()));
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(
            @RequestAttribute(name = ContabilAuthFilter.ATTR_EMAIL, required = false) String email) {
        if (email == null) {
            throw new ModuloException("Não autenticado", HttpStatus.UNAUTHORIZED);
        }
        Contador c = authService.buscarPorEmail(email);
        return ResponseEntity.ok(new MeResponse(c.getEmail(), c.getNome(), props.getEmpresaNome()));
    }
}
