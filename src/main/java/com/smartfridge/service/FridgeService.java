package com.smartfridge.service;

import com.smartfridge.dto.FridgeItemDTO;
import com.smartfridge.entity.FridgeItem;
import com.smartfridge.entity.User;
import com.smartfridge.exceptions.InsufficientStockException;
import com.smartfridge.exceptions.ItemNotFoundException;
import com.smartfridge.repository.FridgeItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class FridgeService {

    FridgeItemRepository fridgeItemRepository;

    @Autowired
    public FridgeService(FridgeItemRepository fridgeItemRepository) {
        this.fridgeItemRepository = fridgeItemRepository;
    }

    public List<FridgeItem> findByUser(User user) {
        return fridgeItemRepository.findByUser(user);
    }

    public List<FridgeItem> findAll() {
        return fridgeItemRepository.findAll();
    }

    public void delete(FridgeItem fridgeItem) {
        fridgeItemRepository.delete(fridgeItem);
    }

    public List<FridgeItem> findByUsername(User user) {
        return fridgeItemRepository.findByUser(user);
    }

    public FridgeItem findById(Integer id) {
        return fridgeItemRepository.findById(id).orElse(null);
    }

    public void deleteById(Integer id) {
        fridgeItemRepository.deleteById(id);
    }

    public List<FridgeItem> findByUserAndCategory(User user, String category) {
        return fridgeItemRepository.findByUserAndCategory(user, category);
    }

    public List<FridgeItem> findByExpiryDateBeforeAndUser(LocalDateTime expiryDate, User user) {
        return fridgeItemRepository.findByExpiryDateBeforeAndUser(expiryDate, user);
    }

    @Transactional
    public FridgeItem create(FridgeItem fridgeItem) {
        // Check if ID is not null (assuming ID is an Integer object)
        if (fridgeItem.getId() != null) {
            throw new IllegalArgumentException("Cannot create a new item with an existing ID: " + fridgeItem.getId());
        }
        return fridgeItemRepository.save(fridgeItem);
    }

    public FridgeItem create(FridgeItemDTO fridgeItemDTO, User user) {
        FridgeItem fridgeItem = new FridgeItem(
                user,
                fridgeItemDTO.getName(),
                fridgeItemDTO.getCategory(),
                fridgeItemDTO.getUnit(),
                fridgeItemDTO.getQuantity(),
                fridgeItemDTO.getExpiryDate(),
                fridgeItemDTO.getAddedVia()
                );

        return fridgeItemRepository.save(fridgeItem);
    }

    /**
     * Updates an existing FridgeItem. Throws an error if the item does not exist.
     */
    @Transactional
    public FridgeItem update(FridgeItem fridgeItem) {
        if (fridgeItem.getId() == null) {
            throw new IllegalArgumentException("Cannot update an item without an ID.");
        }

        // Verify the item actually exists in the database before updating
        boolean exists = fridgeItemRepository.existsById(fridgeItem.getId());
        if (!exists) {
            throw new IllegalArgumentException("Fridge item not found with ID: " + fridgeItem.getId());
        }

        return fridgeItemRepository.save(fridgeItem);
    }

    public long countByNameAndUser(String name, User user){
        return fridgeItemRepository.countByNameAndUser(name, user);
    }

    @Transactional
    public FridgeItem consume(String itemName, Integer id, BigDecimal quantity, User user)
            throws IllegalArgumentException, ItemNotFoundException, InsufficientStockException {
        List<FridgeItem> temp = fridgeItemRepository.findByNameAndUser(itemName, user);

        FridgeItem item = null;

        if (temp.isEmpty())
            throw new ItemNotFoundException("No item found with name: " + itemName);
        else if (temp.size() == 1) {
            item = temp.get(0);
            if (id != null && item.getId().compareTo(id) != 0)
                throw new IllegalArgumentException("Fridge item not found with name: " + itemName + " and ID: " + id);
        } else {
            if (id == null)
                throw new IllegalArgumentException("Please specify ID, multiple instances of " + itemName);
            item = temp.stream().filter(f -> f.getId().equals(id)).findFirst().orElse(null);
            if (item == null) {
                throw new IllegalArgumentException("Fridge item not found with name: " + itemName + " and ID: " + id);
            }
        }

        // Check for stock limits before subtracting
        if (item.getQuantity().compareTo(quantity) < 0) {
            throw new InsufficientStockException("Insufficient stock! You only have " + item.getQuantity() + " left.");
        }
        item.setQuantity(item.getQuantity().subtract(quantity));

        if (item.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
            fridgeItemRepository.delete(item);
            return null;
        }

        //update the quantity in the database
        return fridgeItemRepository.save(item);
    }

    public List<FridgeItem> findAllWithUser() {
        return fridgeItemRepository.findAllWithUser();
    }
}
