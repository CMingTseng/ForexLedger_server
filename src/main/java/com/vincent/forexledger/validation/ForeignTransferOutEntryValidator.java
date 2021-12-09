package com.vincent.forexledger.validation;

import com.vincent.forexledger.model.entry.CreateEntryRequest;
import org.apache.commons.lang3.StringUtils;

import java.util.Optional;

public class ForeignTransferOutEntryValidator implements ICreateEntryValidator {

    @Override
    public boolean validate(CreateEntryRequest request) {
        var isValid = true;

        if (request.getTwdAmount() != null) {
            isValid = false;
        }

        var hasRelatedBookId = StringUtils.isNotBlank(request.getRelatedBookId());
        var hasRelatedForeignAmount = request.getRelatedBookForeignAmount() != null;
        if (hasRelatedBookId ^ hasRelatedForeignAmount) {
            isValid = false;
        }

        var isRelatingBook = hasRelatedBookId && hasRelatedForeignAmount;
        var relatedForeignAmount = Optional.ofNullable(request.getRelatedBookForeignAmount())
                .orElse(0.0);
        if (isRelatingBook && relatedForeignAmount <= 0) {
            isValid = false;
        }

        return isValid;
    }
}
