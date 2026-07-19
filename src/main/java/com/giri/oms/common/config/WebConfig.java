package com.giri.oms.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Applies a single API version prefix ({@code /api/v1}) in front of every
 * {@code @RestController}. This is prepended to whatever the controller's own
 * {@code @RequestMapping} says — so each controller's own mapping must be just its
 * resource path (e.g. {@code @RequestMapping("/products")}), with no {@code /api}
 * of its own, or the effective route ends up doubled as {@code /api/v1/api/products}.
 * <ul>
 *   <li>All seven existing controllers (Auth, Customer, Product, Inventory, Order,
 *       Payment, Shipment) have been updated to this bare-resource-path form.</li>
 *   <li>Any controller added later must follow the same convention — map only the
 *       resource path, and let this config supply {@code /api/v1}. The predicate below
 *       matches on the controller class ({@code @RestController}, yes/no), not on
 *       whether a mapping already contains {@code /api} — so it does not protect
 *       against double-prefixing by itself. A future v2 for one whole controller means
 *       giving that controller its own {@code @RequestMapping} that is excluded from
 *       this predicate (e.g. by checking for a marker annotation or the specific
 *       class), not just writing {@code "/api/v2/..."} into its mapping.</li>
 * </ul>
 * Paths that must stay unversioned — health checks, the OpenAPI/Swagger UI itself —
 * are plain {@code @Controller}/framework-registered endpoints, not
 * {@code @RestController}s in this codebase, so the predicate below already excludes
 * them without an explicit exception list. If that ever changes, exclude them here
 * explicitly rather than relying on this side effect.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    public static final String API_PREFIX = "/api/v1";

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix(API_PREFIX, c -> c.isAnnotationPresent(RestController.class));
    }
}
