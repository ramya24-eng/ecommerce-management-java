package com.example.ecom.services;

import com.example.ecom.exceptions.ProductNotFoundException;
import com.example.ecom.exceptions.UnAuthorizedAccessException;
import com.example.ecom.exceptions.UserNotFoundException;
import com.example.ecom.models.Inventory;
import com.example.ecom.models.Product;
import com.example.ecom.models.User;
import com.example.ecom.models.UserType;
import com.example.ecom.repositories.InventoryRepository;
import com.example.ecom.repositories.ProductRepository;
import com.example.ecom.repositories.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class InventoryServiceImpl implements InventoryService{

    private UserRepository userRepository;
    private InventoryRepository inventoryRepository;
    private ProductRepository productRepository;
    private NotificationRepository notificationRepository;

    private EmailAdapter emailAdapter;

    @Autowired
    public InventoryServiceImpl(NotificationRepository notificationRepository, EmailAdapter emailAdapter,UserRepository userRepository, InventoryRepository inventoryRepository, ProductRepository productRepository) {
        this.userRepository = userRepository;
        this.inventoryRepository = inventoryRepository;
        this.productRepository = productRepository;
        this.notificationRepository = notificationRepository;
        this.emailAdapter = emailAdapter;
    }

    @Override
    public Inventory createOrUpdateInventory(int userId, int productId, int quantity) throws ProductNotFoundException, UserNotFoundException, UnAuthorizedAccessException {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        if(!user.getUserType().equals(UserType.ADMIN)){
            throw new UnAuthorizedAccessException("Only admins can create or update inventory");
        }
        Optional<Inventory> inventoryOptional = inventoryRepository.findByProductId(productId);
        if(inventoryOptional.isEmpty()){
            Product product = this.productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException("Product not found"));
            Inventory inventory = new Inventory();
            inventory.setProduct(product);
            inventory.setQuantity(quantity);
            return inventoryRepository.save(inventory);
        } else{
            Inventory inventory = inventoryOptional.get();
            inventory.setQuantity(inventory.getQuantity() + quantity);
            return inventoryRepository.save(inventory);
        }
    }

    @Override
    public void deleteInventory(int userId, int productId) throws UserNotFoundException, UnAuthorizedAccessException {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException("User not found"));
        if(!user.getUserType().equals(UserType.ADMIN)){
            throw new UnAuthorizedAccessException("Only admins can delete inventory");
        }
        Optional<Inventory> inventoryOptional = inventoryRepository.findByProductId(productId);
        if(inventoryOptional.isPresent()){
            Inventory inventory = inventoryOptional.get();
            inventoryRepository.delete(inventory);
        }
    }

    @Override
    public Inventory updateInventory(int productId, int quantity) throws ProductNotFoundException {
        Product product = this.productRepository.findById(productId).orElseThrow(() -> new ProductNotFoundException("Product not found"));
        Optional<Inventory> inventoryOptional = this.inventoryRepository.findByProduct(product);
        Inventory inventory;
        if(inventoryOptional.isEmpty()){
            inventory = new Inventory();
            inventory.setProduct(product);
            inventory.setQuantity(quantity);
            return this.inventoryRepository.save(inventory);
        }
        inventory = inventoryOptional.get();
        inventory.setQuantity(inventory.getQuantity() + quantity);
        //Send notification to users
        List<Notification> notifications = notificationRepository.findByProduct(product);
        for (Notification notification : notifications) {
            String emailBody = String.format("Dear %s, %s is now back in stock. Grab it ASAP!", notification.getUser().getName(), notification.getProduct().getName());
            String subject = String.format("%s back in stock", notification.getProduct().getName());
            emailAdapter.sendEmail(notification.getUser().getEmail(), subject, emailBody);
            notification.setStatus(NotificationStatus.SENT);
            notificationRepository.save(notification);
        }
        return this.inventoryRepository.save(inventory);
    }
}
