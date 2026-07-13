package br.com.erpkit.whatsapp.config;

import br.com.erpkit.whatsapp.web.AuditoriaAcessoInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serve o app WhatsApp standalone (Angular) empacotado em {@code classpath:/static/browser/}
 * a partir do proprio jar do modulo — mesma origem/porta da API (sem porta nova, sem CORS).
 *
 * <p>Fallback do SPA (HTML5 pushState): devolve o arquivo estatico quando existe; para
 * qualquer rota que NAO seja da API (api/webhook/monitor/health/swagger/v3/actuator/error)
 * devolve o index.html para o roteador do Angular assumir (deep-link e F5). Os controllers
 * tem precedencia sobre este handler, entao /api/whatsapp/**, /webhook/**, /health etc.
 * continuam servindo JSON. O {@code /monitor.html} legado (dev/meta) fica na raiz de
 * {@code static/} e e servido pelo handler default do Spring — a exclusao de "monitor"
 * abaixo evita que rotas /monitor caiam no SPA.</p>
 *
 * <p>O boot NAO quebra sem {@code static/browser/} (o front vem em fase seguinte): o
 * resource handler apenas nao encontra recursos e o {@link StaticNotFoundHandler} devolve 404.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuditoriaAcessoInterceptor auditoriaAcessoInterceptor;

    public WebConfig(AuditoriaAcessoInterceptor auditoriaAcessoInterceptor) {
        this.auditoriaAcessoInterceptor = auditoriaAcessoInterceptor;
    }

    /** Trilha de auditoria (LGPD): so os acessos ao dado do paciente no inbox. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditoriaAcessoInterceptor)
                .addPathPatterns("/api/whatsapp/conversas/**");
    }

    /** Raiz "/" -> index.html do app (o resource handler abaixo serve o restante do SPA). */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/").setViewName("forward:/index.html");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/browser/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        // Raiz "/" chega como "" ou "." dependendo da normalizacao -> index.html.
                        if (resourcePath.isEmpty() || resourcePath.equals(".") || resourcePath.equals("/")) {
                            return location.createRelative("index.html");
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Namespaces da API nunca caem no SPA (seguem e viram 404 JSON).
                        if (resourcePath.startsWith("api/") || resourcePath.startsWith("webhook")
                                || resourcePath.startsWith("monitor") || resourcePath.startsWith("health")
                                || resourcePath.startsWith("swagger-ui") || resourcePath.startsWith("v3/api-docs")
                                || resourcePath.startsWith("actuator/") || resourcePath.equals("error")) {
                            return null;
                        }
                        // Path com extensao que nao existe = asset morto -> 404 (evita HTML servido como JS).
                        String ultimoSegmento = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
                        if (ultimoSegmento.contains(".")) {
                            return null;
                        }
                        // Rota do Angular (sem extensao) -> index.html; o authGuard decide no client.
                        return location.createRelative("index.html");
                    }
                });
    }
}
