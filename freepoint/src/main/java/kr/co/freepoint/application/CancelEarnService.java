package kr.co.freepoint.application;

import kr.co.freepoint.common.exception.PointException;
import kr.co.freepoint.domain.account.PointAccount;
import kr.co.freepoint.domain.account.PointAccountRepository;
import kr.co.freepoint.domain.vo.PointKey;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelEarnService {

    private final PointAccountRepository accountRepository;

    public CancelEarnService(PointAccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public void cancel(String pointKeyValue) {
        PointAccount account = accountRepository.findByEarnPointKeyForUpdate(pointKeyValue)
                .orElseThrow(() -> new PointException("EARN_NOT_FOUND", HttpStatus.NOT_FOUND,
                        "적립 내역을 찾을 수 없습니다: " + pointKeyValue));

        account.cancelEarn(new PointKey(pointKeyValue));
        accountRepository.save(account);
    }
}
