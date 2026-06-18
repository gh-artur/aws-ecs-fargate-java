package br.com.ghartur.aws_project01.service;

import br.com.ghartur.aws_project01.enums.EventType;
import br.com.ghartur.aws_project01.model.Product;
import br.com.ghartur.aws_project01.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductPublisher productPublisher;

    public Iterable<Product> findAll() {
        return productRepository.findAll();
    }

    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    public Optional<Product> findByCode(String code) {
        return productRepository.findByCode(code);
    }

    public Product save(Product product) {
        Product savedProduct = productRepository.save(product);
        productPublisher.publishProductEvent(savedProduct, EventType.PRODUCT_CREATED, "matilde");
        return savedProduct;
    }

    public Optional<Product> update(Long id, Product product) {
        if (productRepository.existsById(id)) {
            product.setId(id);
            Product updatedProduct = productRepository.save(product);
            productPublisher.publishProductEvent(updatedProduct, EventType.PRODUCT_UPDATE, "doralice");
            return Optional.of(updatedProduct);
        }
        return Optional.empty();
    }

    public Optional<Product> delete(Long id) {
        Optional<Product> product = productRepository.findById(id);
        product.ifPresent(p -> {
            productRepository.delete(p);
            productPublisher.publishProductEvent(p, EventType.PRODUCT_DELETE, "lucimar");
        });
        return product;
    }
}
