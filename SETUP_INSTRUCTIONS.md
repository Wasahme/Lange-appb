# 🛠️ تعليمات إعداد بيئة التطوير

## متطلبات النظام

### 1. Java Development Kit (JDK)
```bash
# تثبيت JDK 17
# على Ubuntu/Debian:
sudo apt update
sudo apt install openjdk-17-jdk

# على macOS:
brew install openjdk@17

# على Windows:
# تحميل من: https://adoptium.net/
```

### 2. Android Studio وSDK
```bash
# تحميل Android Studio من:
# https://developer.android.com/studio

# أو تثبيت Android SDK فقط:
# https://developer.android.com/studio#command-tools
```

### 3. متغيرات البيئة
```bash
# إضافة للملف ~/.bashrc أو ~/.zshrc:
export ANDROID_HOME=$HOME/Android/Sdk
export PATH=$PATH:$ANDROID_HOME/tools
export PATH=$PATH:$ANDROID_HOME/tools/bin
export PATH=$PATH:$ANDROID_HOME/platform-tools

# إعادة تحميل الملف:
source ~/.bashrc
```

## إعداد المشروع

### 1. استنساخ المشروع
```bash
git clone <repository-url>
cd BitChat
```

### 2. إنشاء local.properties
```bash
# إنشاء الملف في مجلد المشروع:
echo "sdk.dir=$ANDROID_HOME" > local.properties
```

### 3. منح الأذونات
```bash
chmod +x gradlew
```

### 4. بناء المشروع
```bash
# تنظيف المشروع:
./gradlew clean

# بناء Debug APK:
./gradlew assembleDebug

# بناء Release APK:
./gradlew assembleRelease
```

## حل المشاكل الشائعة

### مشكلة "SDK location not found"
```bash
# التأكد من وجود Android SDK:
ls $ANDROID_HOME

# إنشاء local.properties يدوياً:
echo "sdk.dir=/path/to/your/android/sdk" > local.properties
```

### مشكلة "ANDROID_HOME not set"
```bash
# البحث عن مسار Android SDK:
find /home -name "android-sdk*" 2>/dev/null
find /usr/local -name "android*" 2>/dev/null

# تعيين المتغير مؤقتاً:
export ANDROID_HOME=/path/to/android/sdk
```

### مشكلة Java Version
```bash
# فحص إصدار Java:
java -version

# تغيير إصدار Java (على Ubuntu):
sudo update-alternatives --config java
```

### مشكلة Gradle Permissions
```bash
# منح أذونات التنفيذ:
chmod +x gradlew

# أو استخدام Gradle المثبت عالمياً:
gradle assembleDebug
```

## البناء عبر GitHub Actions

المشروع مُعد للبناء التلقائي عبر GitHub Actions:

1. **Push الكود** إلى repository
2. **Actions تبدأ تلقائياً** وتنزل Android SDK
3. **بناء APK** للـ Debug والـ Release
4. **رفع الملفات** كـ artifacts
5. **إنشاء Release** تلقائياً

## متطلبات الجهاز للاختبار

### Android Device/Emulator:
- **Android 7.0** (API 24) أو أحدث
- **Bluetooth LE** مدعوم
- **4GB RAM** على الأقل
- **2GB Storage** مساحة فارغة

### الأذونات المطلوبة:
- Bluetooth
- Location (للبحث عن أجهزة Bluetooth)
- Camera (للمكالمات المرئية)
- Microphone (للمكالمات الصوتية)
- Storage (لحفظ الملفات)

## اختبار النظام الجديد

### اختبار الشبكة الشبكية:
1. **تثبيت التطبيق** على جهازين أو أكثر
2. **تشغيل البلوتوث** على جميع الأجهزة
3. **فتح شاشة الشبكة الشبكية** في التطبيق
4. **تشغيل النظام** عبر المفتاح
5. **مراقبة اكتشاف الأجهزة** القادرة على التمرير
6. **تجربة إرسال رسالة** ومراقبة التوجيه

### اختبار التمرير العام:
1. **تشغيل أجهزة بلوتوث** أخرى قريبة (سماعات، ساعات ذكية، إلخ)
2. **مراقبة الشاشة** لرؤية الأجهزة المكتشفة
3. **فحص استراتيجيات التمرير** لكل جهاز
4. **اختبار إرسال رسائل** عبر هذه الأجهزة

## الدعم والمساعدة

في حالة واجهة مشاكل:

1. **فحص logs**:
   ```bash
   ./gradlew assembleDebug --debug
   ```

2. **تنظيف المشروع**:
   ```bash
   ./gradlew clean
   rm -rf .gradle/
   ```

3. **إعادة تنزيل Dependencies**:
   ```bash
   ./gradlew --refresh-dependencies
   ```

4. **فحص الإعدادات**:
   ```bash
   ./gradlew properties | grep -i android
   ```

---

## ✅ قائمة التحقق السريعة

- [ ] Java 17 مثبت
- [ ] Android SDK مثبت  
- [ ] ANDROID_HOME معين
- [ ] local.properties موجود
- [ ] gradlew قابل للتنفيذ
- [ ] الاتصال بالإنترنت متاح
- [ ] مساحة كافية على القرص الصلب (5GB+)

عند استكمال جميع الخطوات أعلاه، يجب أن يعمل البناء بنجاح! 🎉