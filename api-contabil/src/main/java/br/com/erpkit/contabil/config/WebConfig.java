package br.com.erpkit.contabil.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;

/**
 * Serve o app contábil standalone (Angular) empacotado em {@code classpath:/static/browser/}
 * a partir do próprio jar do módulo — mesma origem/porta da API (sem porta nova, sem CORS).
 *
 * <p>Fallback do SPA (HTML5 pushState): devolve o arquivo estático quando existe; para
 * qualquer rota que NÃO seja da API (v1/health/api/swagger/v3/actuator/error) devolve o
 * index.html para o roteador do Angular assumir (deep-link e F5). Os controllers têm
 * precedência sobre este handler, então /v1/**, /health etc. continuam servindo JSON.</p>
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    /** Raiz "/" → index.html do app (o resource handler abaixo serve o restante do SPA). */
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
                        // Raiz "/" chega como "" ou "." dependendo da normalização → index.html.
                        if (resourcePath.isEmpty() || resourcePath.equals(".") || resourcePath.equals("/")) {
                            return location.createRelative("index.html");
                        }
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;
                        }
                        // Namespaces da API nunca caem no SPA (seguem e viram 404 JSON).
                        if (resourcePath.startsWith("v1/") || resourcePath.startsWith("health")
                                || resourcePath.startsWith("api/") || resourcePath.startsWith("swagger-ui")
                                || resourcePath.startsWith("v3/api-docs") || resourcePath.startsWith("actuator/")
                                || resourcePath.equals("error")) {
                            return null;
                        }
                        // Path com extensão que não existe = asset morto → 404 (evita HTML servido como JS).
                        String ultimoSegmento = resourcePath.substring(resourcePath.lastIndexOf('/') + 1);
                        if (ultimoSegmento.contains(".")) {
                            return null;
                        }
                        // Rota do Angular (sem extensão) → index.html; o authGuard decide no client.
                        return location.createRelative("index.html");
                    }
                });
    }
}
