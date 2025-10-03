package com.uplatform.wallet_tests.api.kafka.dto;
import com.uplatform.wallet_tests.config.modules.http.HttpServiceHelper;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.uplatform.wallet_tests.api.http.fapi.dto.registration.enums.Gender;

public record PlayerInfo(
        @JsonProperty("id") Integer id,
        @JsonProperty("nodeId") String nodeId,
        @JsonProperty("projectGroupId") String projectGroupId,
        @JsonProperty("externalId") String externalId,
        @JsonProperty("accountId") String accountId,
        @JsonProperty("firstName") String firstName,
        @JsonProperty("lastName") String lastName,
        @JsonProperty("middleName") String middleName,
        @JsonProperty("gender") Gender gender,
        @JsonProperty("birthday") String birthday,
        @JsonProperty("region") String region,
        @JsonProperty("postalCode") String postalCode,
        @JsonProperty("address") String address,
        @JsonProperty("city") String city,
        @JsonProperty("email") String email,
        @JsonProperty("phone") String phone,
        @JsonProperty("country") String country,
        @JsonProperty("currency") String currency,
        @JsonProperty("locale") String locale,
        @JsonProperty("status") Integer status,
        @JsonProperty("createdAt") Long createdAt,
        @JsonProperty("registrationIp") String registrationIp,
        @JsonProperty("iban") String iban,
        @JsonProperty("personalId") String personalId,
        @JsonProperty("placeOfWork") String placeOfWork,
        @JsonProperty("activitySectorInput") String activitySectorInput,
        @JsonProperty("activitySectorAlias") String activitySectorAlias,
        @JsonProperty("avgMonthlySalaryEURInput") String avgMonthlySalaryEURInput,
        @JsonProperty("avgMonthlySalaryEURAlias") String avgMonthlySalaryEURAlias,
        @JsonProperty("jobAlias") String jobAlias,
        @JsonProperty("jobInput") String jobInput,
        @JsonProperty("isPoliticallyInvolved") Boolean isPoliticallyInvolved,
        @JsonProperty("isKYCVerified") Boolean isKYCVerified,
        @JsonProperty("nickname") String nickname,
        @JsonProperty("bonusChoice") String bonusChoice,
        @JsonProperty("profession") String profession,
        @JsonProperty("promoCode") String promoCode,
        @JsonProperty("ip") String ip
) {}