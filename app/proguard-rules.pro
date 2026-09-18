# Activities are kept via the manifest. Add -keep rules here only when you add a
# library that uses reflection or serialization (e.g. Room, Gson, a JSON model).
# The AdMob/UMP SDKs ship their own consumer rules, so nothing is needed for ads.
-dontwarn kotlin.**

# PDFBox-Android references optional back-ends we don't ship: a JPEG2000 decoder
# (gemalto/jai) and desktop java.awt/imageio APIs absent on Android. R8 aborts on
# these missing classes unless told they're expected. We don't use JPEG2000 input.
-dontwarn com.gemalto.jp2.**
-dontwarn jj2000.**
-dontwarn javax.imageio.**
-dontwarn java.awt.**
-dontwarn org.apache.pdfbox.**
# BouncyCastle backs PDF encryption (protect/unlock); keep it and silence its
# optional-provider references.
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
