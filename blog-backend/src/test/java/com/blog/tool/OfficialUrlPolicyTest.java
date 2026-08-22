package com.blog.tool;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class OfficialUrlPolicyTest {
    @Test
    void canonicalizesUnicodeIdnHostAndUnicodePathQueryAndFragment() {
        assertThat(OfficialUrlPolicy.normalize("https://\u4f8b\u5b50.\u6d4b\u8bd5/\u8def\u5f84?\u67e5\u8be2=\u4f60\u597d#\u7247\u6bb5"))
                .isEqualTo("https://xn--fsqu00a.xn--0zwm56d/%E8%B7%AF%E5%BE%84?%E6%9F%A5%E8%AF%A2=%E4%BD%A0%E5%A5%BD#%E7%89%87%E6%AE%B5");
    }

    @Test
    void rejectsMalformedIdnStructuralLookalikesAndInvalidPorts() {
        for (String value : new String[]{"https://-bad.example", "https://bad-.example", "https://example..com",
                "https://example\u3002com", "https://example.com:", "https://example.com:99999",
                "https://example.com:0", "https://example.com:-1"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> OfficialUrlPolicy.normalize(value));
        }
    }

    @Test
    void rejectsInvalidAceAndPercentEncodedControlsWithoutRejectingLegitimateEscapes() {
        for (String value : new String[]{"https://xn--a.example", "https://example.com/%00", "https://example.com/?q=%0d%0a",
                "https://example.com/#%7f"}) {
            assertThatIllegalArgumentException().isThrownBy(() -> OfficialUrlPolicy.normalize(value));
        }
        assertThat(OfficialUrlPolicy.normalize("https://example.com/a%20b?q=%E4%BD%A0%E5%A5%BD"))
                .isEqualTo("https://example.com/a%20b?q=%E4%BD%A0%E5%A5%BD");
    }

    @Test
    void preservesValidCustomPort() {
        assertThat(OfficialUrlPolicy.normalize("https://EXAMPLE.com:8443/docs"))
                .isEqualTo("https://example.com:8443/docs");
    }

    @Test
    void rejectsFinalAsciiUrlBeyondStorageBoundButAllowsExactBoundary() {
        String prefix = "https://example.com/";
        String exact = prefix + "a".repeat(1000 - prefix.length());
        String normalizedExact = OfficialUrlPolicy.normalize(exact);
        assertThat(normalizedExact.length()).isLessThanOrEqualTo(1000);

        String tooLong = prefix + "\u8def".repeat(112);
        assertThatIllegalArgumentException().isThrownBy(() -> OfficialUrlPolicy.normalize(tooLong));
    }
}
