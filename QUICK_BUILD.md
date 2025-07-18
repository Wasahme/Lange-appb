# ⚡ البناء السريع - BitChat

## 🔧 المتطلبات الأساسية
- Java 17
- Android SDK (API 35)
- Git

## 🚀 البناء السريع

### 1. التحضير:
```bash
git clone <repository-url>
cd BitChat
chmod +x gradlew
```

### 2. إعداد Android SDK:
```bash
# إنشاء local.properties
echo "sdk.dir=$ANDROID_HOME" > local.properties

# أو تعيين مسار مباشر:
echo "sdk.dir=/path/to/your/android/sdk" > local.properties
```

### 3. البناء:
```bash
# تنظيف
./gradlew clean

# Debug APK
./gradlew assembleDebug

# Release APK  
./gradlew assembleRelease
```

## 📱 ملفات APK

بعد البناء الناجح ستجد الملفات في:
- `app/build/outputs/apk/debug/app-debug.apk`
- `app/build/outputs/apk/release/app-release-unsigned.apk`

## 🔄 البناء التلقائي

**GitHub Actions** يبني APK تلقائياً عند:
- Push إلى main branch
- إنشاء Pull Request

الملفات متاحة في:
- **Artifacts** tab في GitHub Actions
- **Releases** page للإصدارات الرسمية

## ⚠️ في حالة المشاكل

```bash
# فحص Java
java -version

# فحص Android SDK
echo $ANDROID_HOME
ls $ANDROID_HOME

# إعادة البناء
./gradlew clean
./gradlew assembleDebug --refresh-dependencies
```

## 📋 التحقق السريع

✅ **كل شيء جاهز إذا:**
- `./gradlew clean` يعمل بدون أخطاء  
- متغير `ANDROID_HOME` معين أو `local.properties` موجود
- Java 17 مثبت

---

**نصيحة**: للبناء السريع بدون إعداد محلي، استخدم GitHub Actions! 🎯