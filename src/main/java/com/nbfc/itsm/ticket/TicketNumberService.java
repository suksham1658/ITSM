package com.nbfc.itsm.ticket;

import com.nbfc.itsm.domain.TicketNumberConfig;
import com.nbfc.itsm.domain.TicketNumberConfigRepository;
import com.nbfc.itsm.util.TimeUtc;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;

@Service
public class TicketNumberService {

    private final TicketNumberConfigRepository configRepository;

    public TicketNumberService(TicketNumberConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Transactional
    public String allocate() {
        int year = ZonedDateTime.ofInstant(TimeUtc.now(), ZoneOffset.UTC).getYear();
        TicketNumberConfig config = configRepository.findBySequenceYearForUpdate(year).orElse(null);
        if (config == null) {
            TicketNumberConfig template = configRepository.findFirstByOrderBySequenceYearDesc().orElse(null);
            config = new TicketNumberConfig();
            config.setPrefix(template != null ? template.getPrefix() : "ITSM");
            config.setIncludeYear(template == null || template.isIncludeYear());
            config.setPadding(template != null ? template.getPadding() : 6);
            config.setSequenceYear(year);
            config.setLastAllocated(0L);
            config = configRepository.saveAndFlush(config);
            config = configRepository.findBySequenceYearForUpdate(year).orElse(config);
        }
        long next = config.getLastAllocated() + 1L;
        config.setLastAllocated(next);
        configRepository.save(config);
        String seq = pad(next, config.getPadding());
        if (config.isIncludeYear()) {
            return config.getPrefix() + "-" + year + "-" + seq;
        }
        return config.getPrefix() + "-" + seq;
    }

    private static String pad(long value, int padding) {
        String s = String.valueOf(value);
        int pad = Math.max(4, Math.min(10, padding));
        if (s.length() >= pad) {
            return s;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = s.length(); i < pad; i++) {
            sb.append('0');
        }
        sb.append(s);
        return sb.toString();
    }
}
