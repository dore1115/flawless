package com.dsa.flawless.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LogMaskerTest {

    @Test
    void 비밀번호는_값과_무관하게_가린다() {
        assertThat(LogMasker.maskPassword("p@ssw0rd!")).isEqualTo("****");
    }

    @Test
    void 이름은_첫글자와_마지막글자만_남긴다() {
        assertThat(LogMasker.maskName("홍길동")).isEqualTo("홍*동");
        assertThat(LogMasker.maskName("김철")).isEqualTo("김*");
        assertThat(LogMasker.maskName("남궁민수")).isEqualTo("남**수");
    }

    @Test
    void 이메일은_아이디_앞두글자와_도메인만_남긴다() {
        assertThat(LogMasker.maskEmail("hong.gildong@corp.com")).isEqualTo("ho***@corp.com");
    }

    @Test
    void 연락처는_앞자리와_뒤4자리만_남긴다() {
        assertThat(LogMasker.maskPhone("010-1234-5678")).isEqualTo("010-****-5678");
        assertThat(LogMasker.maskPhone("01012345678")).isEqualTo("010-****-5678");
        assertThat(LogMasker.maskPhone("02-123-4567")).isEqualTo("02-****-4567");
    }

    @Test
    void 주소는_시구_단위까지만_남긴다() {
        assertThat(LogMasker.maskAddress("서울특별시 강남구 테헤란로 123")).isEqualTo("서울특별시 강남구 ***");
    }

    @Test
    void 자유_텍스트_속_개인정보_패턴을_가린다() {
        String text = "email=hong@corp.com, phone=010-1234-5678, rrn=900101-1234567, itemId=42";
        assertThat(LogMasker.maskText(text))
                .isEqualTo("email=ho***@corp.com, phone=010-****-5678, rrn=900101-*******, itemId=42");
    }
}
