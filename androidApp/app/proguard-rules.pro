# OpenBite release 的 R8 规则。
#
# 这里故意没有 keep：用到的库都自带规则，R8 会自动合并（见 build/outputs/mapping/release/configuration.txt）。
# - kotlinx.serialization：保留 @Serializable 类的 Companion / serializer() / INSTANCE。
#   种子 JSON、口味档案、.openbite 清单、Claude 流式 DTO、小票 DTO 都用编译期生成的序列化器（decodeFromString<T>() /
#   T.serializer()），字段名写在生成的描述符里，类名被混淆也不影响。
#   Navigation 3 的返回栈按类名反射找 NavKey 的序列化器（Class.forName + serializer(KClass)），
#   同一个 APK 里存和取用的是同一个混淆名，靠上面这条规则就够。
# - WorkManager：保留 ListenableWorker 子类的类名和构造函数（MealFollowUpWorker、MilestoneCheckWorker）。
# - androidx.appfunctions：保留 @AppFunctionSerializable 类名、生成的 Factory 和 Inventory；
#   OpenBiteAppFunctionService 在清单里，AAPT 会生成 keep；assets/openbite_app_function_service.xml 是资源，不受 R8 影响。
# - Room、Koin、Ktor、OkHttp、MapLibre、DataStore、协程：各自 AAR/JAR 里带 consumer 规则。
# - Koin 用 DSL 里的 lambda 构造对象，不靠反射。
#
# 以后真机上出现 ClassNotFoundException / SerializationException 再按 mapping.txt 加最小的规则。
