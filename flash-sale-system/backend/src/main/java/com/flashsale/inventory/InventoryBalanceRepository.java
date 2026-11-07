package com.flashsale.inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryBalanceRepository extends JpaRepository<InventoryBalance, Long> {
    @Modifying
    @Query("UPDATE InventoryBalance i SET i.reservedStock = i.reservedStock + :quantity WHERE i.saleId = :saleId AND i.reservedStock + i.soldStock + :quantity <= i.totalStock")
    int reserveStock(@Param("saleId") Long saleId, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE InventoryBalance i SET i.reservedStock = i.reservedStock - :quantity, i.soldStock = i.soldStock + :quantity WHERE i.saleId = :saleId AND i.reservedStock >= :quantity")
    int commitStock(@Param("saleId") Long saleId, @Param("quantity") int quantity);

    @Modifying
    @Query("UPDATE InventoryBalance i SET i.reservedStock = i.reservedStock - :quantity WHERE i.saleId = :saleId AND i.reservedStock >= :quantity")
    int releaseStock(@Param("saleId") Long saleId, @Param("quantity") int quantity);
}