package com.hayat.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MedicalDatabaseSeeder(private val context: Context) {
    private val TAG = "MedicalDatabaseSeeder"

    private val seederScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val emergencyProtocols = listOf(
        MedicalChunk(
            protocol_title = "نزيف حاد",
            content = "للتعامل مع النزيف الحاد: اضغط على الجرح بقطعة قماش نظيفة مباشرة. ضع الضحية في وضعية لياسة (إذا كان واعياً). اطمئن على نفسها. تجنب استخدام زيت أو معجون على النزيف.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "صدمة انفارغوني",
            content = "علامات الانفارغوني: توسعت رقبة، احمرار العيون، اضطراب نزيف الدم إلى الأنسجة. اعتنق وضعية رأس مرفوع. ابقَ بعيداً عن الضوء المباشر. أرسل إلى المستشفى فوراً.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "كدمة دموية",
            content = "للكدمة الدموية: ضع الجزء المصاب في وضعية أعلى من مستوى القلب. طبق قطيفة صلبة فوق المنطقة. لا تطبق ضغط مباشر على الكدمة. راجع طبيباً في أقرب وقت.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "كوع مكدول",
            content = "علامات الكوع المكدول: تورم وانتعاش سريع للورم. يفضي إلى احتقان العضلات. لا تحاول تصحيح الكوع. استخدم splint طارئاً. أرسل إلى المستشفى.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "كسر عظمي",
            content = "للكسر العظمي: لا تحاول تماثل الأطراف. احمي المنطقة المصابة. ثبت المفصل إذا كان مكدولاً. لا تضع وزن على الكسر. أرسل إلى المستشفى.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "حروق الدرق",
            content = "للحروق بتركيز درق: اشطف المنطقة بماء هادئ 10-20 دقيقة. لا تستخدم ماء مائع أو صابون. ارفع المنطقة. غطي الجرح بغلاف رخيص. لا تضع مرهماً على الجرح.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "حروق طبقية دقيقة",
            content = "للحروق الطبقية الدقيقة: احرص على عدم تلف الجلد. احفظ فسيولوجياً. لا تشطف بالماء. استخدم مراهم صلبة كمانشوت. حافظ على الجرح مبرداً.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "أنفارغون صدمة انفارغوني",
            content = "أنفارغون: اعتنق وضعية أرجفة الجانب. لا تُعطل خط الجهاز القلبي الرئوي. لا تحرك المصاب بشكل عميق. فك الحزام إذا كان موضعه مريحاً.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "انخفاض ضغط الدم",
            content = "علامات انخفاض ضغط الدم: غثيان، دوخة، تعرق. ضع المريض في وضع الاستراحة. ارفع الساقين فوق مستوى القلب. شرب سريع للماء إذا كان متوعماً. أرسل إلى المستشفى.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "نوبة سكر",
            content = "علامات نوبة السكر: اضطراب التوازن، توارغ العينين إلى جانبين، اصابة للوجه، عدم وعي. اشرب سكر سهل إذا وجهت إليه. اتصل بـ 911 على الفور. لا تطعم شخاخاً غير واعياً.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "الإنعاش القلبي الرئوي",
            content = "إنعاش قلبي رئوي فرعي: ضع اليد على موضع العظام between الصدر والسكر. ضعظه في وسط الأنسجة بين الصدر والسكر. اضغط بسرعة 100-120 ضغطة في الدقيقة. ابحث عن جذع الرئة إذا كان متوهمًا.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "اختناق",
            content = "لاختناق فرد: اطقة الضحية بضغط على البطن. جعل 5 واحدات لاختناق. اتركه إذا لم يتحلل الاختناق. نفس التنفس فوق الضحية. لا تفعل هذا على أطفال. لا تستخدم طريقة الانقباض.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "اختناق فراشة",
            content = "الإنعاش عن اختناق الفراشة: لا تجر طفلاً صغير. ضع على جانبي العصب. لا تجر الفراشة. استخدم مآخذ الفم. اطق باستخدام طريقة هيبتون للرضع.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "التهاب الجيوب الأنفية",
            content = "للتهاب الجيوب: ضع ماء ساخن على المنطقة. لا تستخدم قطرات في الأنف. لا تستخدم سداءً للأنف. احتفظ به بضعية جانبية. كوى الصدأ إذا كان حاديًا.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "تسمم غذوي",
            content = "لتسمم الغذائي: ابقِ المريض على الأرض. لا تقمع الغثيان. لا تعطه أي شيء عن طريق الفم إذا كان سيئ الغثيان. اعطي سوائل معالجة عن طريق الوريد إذا كان متوفيًا. ارفع الطرف عن الأرض.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "سكتة دماغية",
            content = "علامات سكتة دماغي: اضطراب الرؤية، توتر العضلات، صعوبة في الكلام. ابدأ العلاج الفوري. اطمئن على المصاب. لا تحركه إلا للضرورة. إمساك الوقت ينقذ الأرواح.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "كوارع الصدمة",
            content = "لكسر الصدمة: لا تجعل الحركة قصيرة. توقف عن الحركة الفورية. لا تحركه إلا إذا كان في خطر. استخدم مانع الصدمة لتثبيت الجمجمة. أرسله للمستشفى.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "الوخز بالعقرب",
            content = "للوخز بالعقرب: ضع المصاب تحت شمس. ابقَ بعيداً عن المنطقة. ثمانية معالجة في 24 ساعة. لا تقمع الوخز بالماء. لا تستخدم ماء القطر على الجرح.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "الوخز بالنحل",
            content = "للوخز بالنحل: ارفع المنطقة. ابقَ بعيداً عن الجسم الحي. ثمانية مراهم. لا تقمع بالماء. أرسل المصاب إذا كان في حالة غير طبيعية.",
            embedding = null
        ),
        MedicalChunk(
            protocol_title = "الوخز بالثعلب",
            content = "للوخز بالثعلب: نظافة الجرح فوراً. لا تقمع الوخز. اللقاح محتمل. استشارة طبيب في أقرب وقت. لا تستخدم الصدمة.",
            embedding = null
        )
    )

    fun seedDatabaseIfNeeded() {
        seederScope.launch {
            try {
                val db = AppDatabase.getDatabase(context)
                val dao = db.medicalChunkDao()

                val existingChunks = dao.getAllChunks()
                if (existingChunks.isEmpty()) {
                    Log.d(TAG, "Seeding database with ${emergencyProtocols.size} emergency protocols")
                    emergencyProtocols.forEach { chunk ->
                        dao.insertChunk(chunk)
                    }
                    Log.d(TAG, "Database seeding complete")
                } else {
                    Log.d(TAG, "Database already has ${existingChunks.size} chunks, skipping seed")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to seed database", e)
            } finally {
                seederScope.cancel()
            }
        }
    }
}