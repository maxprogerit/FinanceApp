package com.smartfinance.dashboard.repository;

import com.smartfinance.dashboard.model.UserSubscription;
import com.smartfinance.dashboard.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserSubscriptionRepository extends JpaRepository<UserSubscription, Long> {

    Optional<UserSubscription> findByUser(User user);

    Optional<UserSubscription> findByStripeCustomerId(String stripeCustomerId);

    Optional<UserSubscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
