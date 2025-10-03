package com.uplatform.wallet_tests.api.db;

import com.uplatform.wallet_tests.api.attachment.AllureAttachmentService;
import com.uplatform.wallet_tests.api.db.repository.player.AccountPropertyRepository;
import com.uplatform.wallet_tests.api.db.repository.player.AccountPropertyStatusProjection;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
public class PlayerDatabaseClient extends AbstractDatabaseClient {

    private final AccountPropertyRepository accountPropertyRepository;

    public PlayerDatabaseClient(AllureAttachmentService attachmentService,
                                AccountPropertyRepository accountPropertyRepository) {
        super(attachmentService);
        this.accountPropertyRepository = accountPropertyRepository;
    }

    public List<Map<String, Object>> findAccountPropertiesByPlayerUuidOrFail(String playerUuid) {
        String description = String.format("player account properties for player '%s'", playerUuid);
        String attachmentNamePrefix = String.format("Player Account Properties [Player: %s]", playerUuid);
        Supplier<Optional<List<Map<String, Object>>>> querySupplier = () -> {
            List<AccountPropertyStatusProjection> result =
                    accountPropertyRepository.findStatusesByPlayerUuid(playerUuid);
            if (result == null || result.isEmpty()) {
                return Optional.empty();
            }
            List<Map<String, Object>> mapped = result.stream()
                    .map(r -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", r.getNameValue());
                        map.put("status", r.getStatusValue() != null && r.getStatusValue() ? 1 : 0);
                        return map;
                    })
                    .collect(Collectors.toList());
            return Optional.of(mapped);
        };
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier);
    }

    public Map<String, Object> waitForAccountPropertyStatus(String playerUuid,
                                                            String propertyName,
                                                            int expectedStatus,
                                                            Duration timeout) {
        String description = String.format("player account property '%s' for player '%s'",
                propertyName, playerUuid);
        String attachmentNamePrefix = String.format(
                "Player Account Property [Player: %s, Property: %s]",
                playerUuid, propertyName);
        Supplier<Optional<Map<String, Object>>> querySupplier = () -> {
            List<AccountPropertyStatusProjection> result =
                    accountPropertyRepository.findStatusesByPlayerUuid(playerUuid);
            if (result == null || result.isEmpty()) {
                return Optional.empty();
            }
            return result.stream()
                    .filter(r -> propertyName.equals(r.getNameValue()))
                    .findFirst()
                    .flatMap(r -> {
                        int status = r.getStatusValue() != null && r.getStatusValue() ? 1 : 0;
                        if (status != expectedStatus) {
                            return Optional.empty();
                        }
                        Map<String, Object> map = new HashMap<>();
                        map.put("name", r.getNameValue());
                        map.put("status", status);
                        return Optional.of(map);
                    });
        };
        return awaitAndGetOrFail(description, attachmentNamePrefix, querySupplier, timeout);
    }
}
