package vn.tailinh.speakmate.service.google;

public interface GoogleIdentityVerifier {

    GoogleIdentity verify(String idToken);
}
