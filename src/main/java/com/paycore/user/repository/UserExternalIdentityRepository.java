package com.paycore.user.repository;

import com.paycore.user.entity.User;
import com.paycore.user.entity.UserExternalIdentity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserExternalIdentityRepository extends JpaRepository<UserExternalIdentity, Long> {

    Optional<UserExternalIdentity> findByProviderAndProviderSubject(String provider, String providerSubject);

    Optional<UserExternalIdentity> findByProviderAndUser(String provider, User user);

    List<UserExternalIdentity> findByUser(User user);

    boolean existsByProviderAndProviderSubject(String provider, String providerSubject);
}
