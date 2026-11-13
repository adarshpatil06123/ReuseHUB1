package com.reusehub.dispute;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DisputeRepository extends JpaRepository<Dispute, UUID> {
  Optional<Dispute> findByOrder_Id(UUID orderId);

  List<Dispute> findByOrder_Buyer_IdOrOrder_Seller_IdOrderByOpenedAtDesc(Long buyerId, Long sellerId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from Dispute d join fetch d.order where d.id = :id")
  Optional<Dispute> findByIdForUpdate(@Param("id") UUID id);
}
