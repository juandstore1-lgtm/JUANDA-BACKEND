package com.jdqstore.backend.security;

import com.jdqstore.backend.entity.User;
import com.jdqstore.backend.repository.CategoryRepository;
import com.jdqstore.backend.repository.ProductRepository;
import com.jdqstore.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Limita las escrituras de productos y categorías de un STORE_ADMIN a sus sedes. GLOBAL_ADMIN no tiene límite.
 * Un STORE_ADMIN solo puede tocar productos cuyas sedes sean todas suyas (no puede quitar un producto de otra
 * sede) y categorías de una de sus sedes; los productos sin sede y las categorías globales son de GLOBAL_ADMIN.
 * Si el producto o la categoría no existe no se comprueba nada y el servicio responde como antes.
 */
@Service
@RequiredArgsConstructor
public class SedeAccessService {

    private static final String GLOBAL_ADMIN = "GLOBAL_ADMIN";

    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    public void checkProductCreate(List<Long> storeIds) {
        Set<Long> allowed = scopedStoreIds();
        if (allowed != null) {
            requireWithin(storeIds, allowed);
        }
    }

    public void checkProductUpdate(Long id, List<Long> storeIds) {
        Set<Long> allowed = scopedStoreIds();
        if (allowed != null) {
            requireExistingProductWithin(id, allowed);
            // storeIds null: ProductService conserva las sedes actuales
            if (storeIds != null) {
                requireWithin(storeIds, allowed);
            }
        }
    }

    public void checkProductDelete(Long id) {
        Set<Long> allowed = scopedStoreIds();
        if (allowed != null) {
            requireExistingProductWithin(id, allowed);
        }
    }

    public void checkCategoryCreate(Long storeId) {
        Set<Long> allowed = scopedStoreIds();
        if (allowed != null) {
            requireWithin(storeId == null ? List.of() : List.of(storeId), allowed);
        }
    }

    public void checkCategoryUpdate(Long id, Long storeId) {
        Set<Long> allowed = scopedStoreIds();
        if (allowed != null) {
            requireExistingCategoryWithin(id, allowed);
            // CategoryService siempre reemplaza la sede (null la convierte en categoría global)
            requireWithin(storeId == null ? List.of() : List.of(storeId), allowed);
        }
    }

    public void checkCategoryDelete(Long id) {
        Set<Long> allowed = scopedStoreIds();
        if (allowed != null) {
            requireExistingCategoryWithin(id, allowed);
        }
    }

    /** null si el usuario actual no tiene límite de sede (GLOBAL_ADMIN); si no, sus sedes. */
    private Set<Long> scopedStoreIds() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserDetailsImpl principal)) {
            throw new AccessDeniedException("Usuario no autenticado");
        }
        User user = principal.getUser();
        if (user.getRole() != null && GLOBAL_ADMIN.equals(user.getRole().getName())) {
            return null;
        }
        return new HashSet<>(userRepository.findStoreIdsById(user.getId()));
    }

    private void requireExistingProductWithin(Long id, Set<Long> allowed) {
        if (id != null && productRepository.existsById(id)) {
            requireWithin(productRepository.findStoreIdsById(id), allowed);
        }
    }

    private void requireExistingCategoryWithin(Long id, Set<Long> allowed) {
        if (id != null && categoryRepository.existsById(id)) {
            Long storeId = categoryRepository.findStoreIdById(id).orElse(null);
            requireWithin(storeId == null ? List.of() : List.of(storeId), allowed);
        }
    }

    private static void requireWithin(Collection<Long> storeIds, Set<Long> allowed) {
        if (storeIds == null || storeIds.isEmpty() || storeIds.stream().anyMatch(id -> id == null || !allowed.contains(id))) {
            throw new AccessDeniedException("La operación afecta a una sede que no tiene asignada");
        }
    }
}
