# Phase 0 — tinyMediaManager 源码考古报告(Media Manager Lite)

> 本报告基于对 tinyMediaManager v5.3.4-SNAPSHOT(`devel` 分支,Apache-2.0)五个子系统的只读源码分析:
> Metadata 模型、Scraper 框架与 TMDB provider、NFO connector、Artwork、Renamer,以及全局基础设施与许可约束。
> 所有类名 / 文件路径均可在本仓库 `src/main/java/org/tinymediamanager/` 下验证。

---

## 0. 结论摘要(TL;DR)

1. **TMM 的可复用价值在"行为规范",不在"代码本身"。** 核心实体(Movie 2956 行、MediaEntity 1706 行)与 `MovieModuleManager` / `Settings` / `TmmTaskManager` 全局单例在生命周期方法体内深度纠缠,持久化是 **H2 MVStore 键值库 + Jackson JSON 整体序列化**(无 ORM、无 SQL schema),直接搬代码 = 搬走整个 JVM 生态,与"低内存、Docker、ARM64、SQLite"目标冲突。
2. **五块黄金资产值得按规范移植(语言无关):**
   - NFO connector 的**完整元素清单 + 三种 flavor 差异 + 用户数据保留规则**(§3.3);
   - TMDB provider 的**中文/多语言回退链、通用集名过滤、剧集组映射、搜索级联与评分、artwork 语言/票数排序**(§3.2);
   - Renamer 的**token 模板系统、文件名净化管线、Kodi/Emby/Jellyfin 文件命名枚举、dry-run 纯函数设计**(§3.5);
   - Artwork 的**下载原子性模式与 `sortArtworkUrls` 排序算法**(§3.4);
   - Scraper 工具的**相似度评分(StrikeAMatch + Jaro-Winkler)**。
3. **技术栈结论:Go + SQLite + HTMX。** 唯一保留 Java 的理由("直接复用实体/scraper 代码")经考古被否定——纠缠面太大且 JVM 内存占用超标。TMM 的 TMDB provider 共 7151 行,其中约 60% 是纯 API 映射,Go 重写成本低;有价值的 40% 已在上面 §3.2 逐条列出。
4. **许可红线:** 本仓库代码 Apache-2.0(复用需保留 NOTICE/许可声明);**闭源 JAR `org.tinymediamanager:license:5.3.1` 中内嵌的默认 TMDB API key 是 TMM 的商业资产,严禁提取复用**——必须注册自己的免费 key;不得使用 "tinyMediaManager" 名称/品牌,不得调用其 license/更新服务器。
5. TMM 自带 CLI(`movie -u -n -r -w` 可全流程 headless)和 JDK HttpServer 的 `/api/` 命令式 API,证明 core 层可完全脱离 Swing 运行——但也仅止于此,`/api/v2` 与 Web UI 在本分支不存在,一切需要新写。

---

## 1. TMM 全局架构事实(考古基线)

| 维度 | 事实 |
|---|---|
| 构建 | 单模块 Maven,Java 17,`org.tinymediamanager:tinyMediaManager:5.3.4-SNAPSHOT` |
| 许可 | 本仓库 Apache-2.0;依赖闭源构件 `org.tinymediamanager:license:5.3.1`(私有 Maven 仓库,内含 `TmmFeature`/`License` 与默认 API key) |
| 持久化 | **无 ORM**。实体库 = H2 `h2-mvstore:2.4.240` 键值库(`data/movies.db`、`data/tvShows.db`),每实体一条 JSON(`MVMap<UUID,String>` + Jackson 2.21.2);API key 存 `core/TmmStore.java`(`tmm.db`,AES 加密);设置 = JSON 文件(`data/tmm.json`、`movies.json`、`tvShows.json`) |
| XML/NFO | **无 JAXB**。全部手写 W3C DOM + Transformer(`core/NfoUtils.java`);解析用 JSoup(`MovieNfoParser` 2119 行等) |
| HTTP | OkHttp 4.12 + Retrofit 2 + Gson(scraper);共享客户端 `scraper/http/TmmHttpClient.java`(429 重试、25MB 磁盘缓存、代理、Brotli) |
| 模板 | JMTE 7.0.3(renamer/export) |
| 已有 HTTP API | JDK `com.sun.net.httpserver`,`/api/` 前缀 + `api-key` 头鉴权 + 内置 OpenAPI 文档 handler;`/api/movie`、`/api/tvshow` 接收 `{action, scope, args}` JSON 并入队任务(即发即忘,无查询能力);**`/api/v2` 与 React webapp 在本分支不存在**(仅 `docs/WEB_UI.md` 计划) |
| Headless 能力 | CLI(picoCLI:`movie`/`tvshow` 子命令)已可 `scan → scrape → rename → writeNFO` 全流程无 Swing;测试以 `-Djava.awt.headless=true` 运行 |
| 任务框架 | `core/threading/TmmTaskManager`:main 队列单线程顺序执行 + imageDownload(3)/imageCache/unnamed/download 等扇出池;`TmmTask` 有进度/协作式取消;**本质上就是一个简单 worker queue,映射容易** |
| 启动链 | `TinyMediaManager.main` → `License.init531()` → headless 分支 → `startup()`:`doUpgradeTasks → loadInternals(genres/langs/ImageCache/MessageManager/TmmHttpServer) → loadModules(Movie/TvShowModuleManager.startUp: 开 MVStore→load JSON→initializeAfterLoading→建索引→2s 定时 flush) → loadPlugins(MediaProviders) → doPostStartupTasks` |

### 全局单例热点(fan-in 最高的耦合源)

`MovieModuleManager.getInstance()` / `TvShowModuleManager.getInstance()`(实体生命周期、saveToDb、settings 读取)、`Settings.getInstance()`(全局日期字段、imageCache 开关)、`TmmTaskManager.getInstance()`(实体内部直接投递图片下载任务)、`MessageManager.getInstance()`、`Globals.*` 静态路径。**实体方法体直接调用这些单例**是复用实体的最大障碍(证据:`Movie.java:238,1085`、`MovieSet.java:125,587`、`TvShowEpisode.java:987`、`MediaEntity.java:714,1469`)。

---

## 2. 依赖图

### 2.1 实体继承与持久化

```
Movie / MovieSet / TvShow / TvShowSeason / TvShowEpisode / Person / MediaTrailer
        └── MediaEntity (abstract, 1706 LOC, Jackson @JsonProperty)
                └── AbstractModelObject (142 LOC)
                      └── javax.swing.event.SwingPropertyChangeSupport  ← 唯一 Swing 编译期依赖
                          (无监听器时 fire 短路,headless 可运行,但拖 Swing jar 进 classpath)

MediaFile (1928 LOC) ── 嵌入于 MediaEntity.mediaFiles(单向 1:N,JSON 内联,无回引用)
MediaFileAudioStream / MediaFileSubtitle ── MediaStreamInfo
MediaGenres / MediaSource ── scraper 包的 DynaEnum(跨包继承)
MediaRating / MediaEntityFilenameHistory ── 纯 POJO,零依赖(完全干净)

Movie ─(持久化 movieSetId: UUID,运行时经 MovieModuleManager 回链)→ MovieSet
TvShowEpisode/Season ─(持久化 tvShowId/tvShowDbId,同上回链)→ TvShow
```

### 2.2 模块级耦合(core ↔ scraper ↔ ui)

```
core.entities ──继承──▶ scraper.DynaEnum            (反向依赖!)
scraper.MediaMetadata ──import──▶ core.entities.{Person, MediaGenres, MediaRating, MediaTrailer}, core.MediaAiredStatus
scraper.interfaces.IMediaProvider ──extends──▶ 闭源 license.TmmFeature(isFeatureEnabled/getApiKey)
scraper.interfaces.*Provider 接口方法签名 ──参数──▶ core.movie/core.tvshow 的 SearchAndScrapeOptions 类
core.AbstractSettings ──import──▶ ui.ITmmUIFilter(拖入 Swing,另一处 core→ui 反向依赖)
core.movie/tasks/MovieScrapeTask ──import──▶ ui.movies.dialogs.MovieChooserDialog(仅 headless 守卫,不实例化)

结论:core 与 scraper 互相依赖、且都渗入 ui 与闭源构件——不存在可整体剪出的"纯核心 JAR"。
```

### 2.3 业务管道依赖(scanner → renamer)

```
MovieUpdateDatasourceTask (scan/parse/NFO 导入)
  → MovieList (内存注册表 + byDbId/byPath 索引, MVStore 加载)
  → MediaProviders → TmdbMovieMetadataProvider (search/score/details)
  → MovieScraperMetadataConfig (字段开关) → MediaMetadata → 赋值到 Movie
  → MovieArtworkHelper.sortArtworkUrls (选图) → MediaEntityImageFetcherTask (下载/落盘) → ImageCache
  → Movie.writeNFO → MovieConnectors 枚举 → MovieGenericXmlConnector (+Kodi/Emby/Jellyfin 覆写) → DOM → NfoUtils
  → MovieRenamer (JMTE 模板 → createDestination 净化 → move/copy → cleanup) ← MovieRenamerProfile (设置)
每一步都读写: MovieModuleManager(settings/DB)、Settings、TmmTaskManager、MessageManager
```

---

## 3. 五大子系统考古细节

### 3.1 Metadata 模型

- **干净可独立复用**(无 GUI、无单例、仅 Jackson):`Person`(414)、`MediaRating`(182)、`MediaGenres`(398, ResourceBundle 本地化)、`MediaSource`、`MediaStreamInfo` 系列、`MediaTrailer`、`MediaEntityFilenameHistory`;scraper 侧 `MediaMetadata`(1484,零注解通用元数据容器)、`MediaSearchResult`(662,含评分)。
- **纠缠必须重写**:`Movie`(2956)/`MovieSet`(877)/`TvShow`(2859)/`TvShowEpisode`(2236)/`TvShowSeason`(599)/`MediaEntity`(1706)/`MediaFile`(1928)——JSON 持久化注解 + 生命周期方法体调用模块管理器/任务管理器/全局 Settings。
- 关系模型有价值且简单:实体间仅存 **UUID 引用 + 加载时回链**;MediaFile 单向内嵌;`ids` 为 `Map<String,Object>`(TMDB_SET、IMDB 等常量键)——这个形状可直接照搬进 Go struct + SQLite。
- 设置类 `AbstractSettings` 甚至 import `ui.ITmmUIFilter`(core→ui 反向依赖),Java 侧已无干净边界。

### 3.2 Scraper 框架与 TMDB provider

框架:`MediaProviders` 硬编码注册表 + `ServiceLoader` addon SPI;接口族 `IMovieMetadataProvider`/`ITvShowMetadataProvider`/`IMovieArtworkProvider`/…;数据载体 `MediaSearchResult`(评分)/`MediaMetadata`(容器)。**整个 scraper 包零 Swing**(仅 `util/VideoPHash` 用 awt 图像类)。

TMDB provider(7151 行,`scraper/tmdb/`):Retrofit+OkHttp+Gson;每子刮削器独立配置文件(AES 加密 key);429 由 `TmmHttp429RetryInterceptor` 处理(Retry-After,≤3 次);15 分钟强制响应缓存;集列表 600s TTL 内存缓存。**搜索与详情带 `language` 参数(zh→zh-CN 扩展),images 请求不带 language(取全语言后客户端排序)**。

**必须按规范移植的 40% 积累逻辑(Phase 1/3 的验收清单):**

1. **语言标签扩展** `getRequestLanguage`:ISO-639-1 裸码 → `language_COUNTRY`(zh→zh-CN)。
2. **翻译回退链** `injectTranslations`:精确 locale → 语言或国家松匹配(排除 es_MX/pt_BR/fr_CA)→ 配置的 fallback 语言 → 原文语言 → 英语;`primary_translations` 全表支持 zh-CN/zh-HK/zh-SG/zh-TW。
3. **通用集名过滤**:剧集列表按最多 4 种语言(请求→fallback→原文→英)合并,`isGenericTitle` + `EPISODE_TRANS` 31 个本地化"第 N 集"词表(含 `第  集`)剔除机器翻译——**没有它,zh-CN 刮削的剧集列表全是"第 1 集"垃圾**。
4. **剧集组映射** `mapEpisodeGroup`:TMDB group type 2→绝对、3→DVD、1/4-7→ALTERNATE(1 本是 aired,因 TMM 不支持双 aired),组内序号 +1(TMDB 从 0 计),按 TMDB episode id → aired S/E → 首播日期三段匹配。
5. **搜索级联**:tmdbId 直取 → imdb/tvdb 经 `/find` → 文本搜索(纯数字串再试一次作为 id)→ 翻页 ≤5 → 去尾部年份重试一次。
6. **评分算法** `MetadataUtil.calculateScore`:`max(StrikeAMatch(字母对相似), 去符号后 StrikeAMatch, 去年份后 StrikeAMatch, Jaro-Winkler)` − 年份罚(0.01+diff/1000,上限 0.11;无年份 0.11)− 0.01 无海报;**id 命中直接 1.0**;TreeSet 按 score↓→year→hashCode。⚠️ 字母对算法需按 UTF-16 pair 验证 CJK 行为。
7. **Artwork 排序**:语言(请求语言 → en)→ `vote_average` 降序;尺寸阶梯 poster(original/w780/w500/w342/w185)、fanart(w1280/w300);clearlogo `.svg`→`.png` 重写;季海报逐季枚举。
8. **分级/上映日期按国家过滤** `release_dates`:type 1 首映需显式开启,type>1 为院线/实体/数字;回退全局 release_date。
9. **staff 职务映射**(Director/Writing→Writer/Producer/Editor/Composer/DoP)、人物头像 `h632` 尺寸、外部 id 全集(imdb/tvdb/wikidata/tvrage/社交)。
10. 频道端点清单(§3.2 末):`/configuration`(验 key + 图片 base_url)、`/search/movie|tv|collection`、`/find/{id}`、`/movie/{id}`(+translations,credits,keywords,release_dates,external_ids)、`/movie/{id}/images`、`/collection/{id}[/images]`、`/tv/{id}`(+translations,credits,external_ids,content_ratings,keywords,episode_groups)、`/tv/{id}/images`、`/tv/{id}/season/{n}[/images|/translations]`、`/tv/{id}/season/{n}/episode/{e}`(+external_ids,credits,images;`include_image_language=null`)、`/tv/episode_groups/{id}`、可选 `/person/{id}/external_ids`。全部 `api_key` query 参数。

耦合面(Go 重写需自备的替身):API key 来源(替换闭源 `TmmFeature`,**用自己的 key**)、options 结构、genre id→本地枚举表、相似度函数(~330 行纯数学)、`removeSortableName`(冠词剥离)。

### 3.3 NFO connector(兼容性规范的来源)

- **写作机制**:手写 DOM;UTF-8、`standalone="yes"`、2 空格缩进、CRLF、头部注释 `<!-- created on ... by tinyMediaManager <ver> for <CONNECTOR> -->`;内容(去除注释后)不变则跳过写入。
- **文件命名/落位**:电影 `<basename>.nfo` 和/或 `movie.nfo`(stacked/disc 强制 basename);剧集 `<basename>.nfo`(多集 = 单文件多个 `<episodedetails>` 根,XML 声明仅首个);剧集组 `season%02d.nfo` 或季文件夹内 `season.nfo`;剧 `tvshow.nfo`;合集 `<dataFolder>/<setName>[-tmdb-<id>]/<setName>.nfo | set.nfo | collection.nfo`。
- **电影 NFO 元素序**(完整清单见 agent 原始报告,写作时的固定顺序):title → originaltitle → sorttitle → year → ratings(Kodi 式 `<rating name max default><value><votes>`,TMDB 改名 `themoviedb`,恰一个 `default="true"`)→ userrating → set(`tmdbcolid` 属性 + name/overview)→ plot/outline/tagline/runtime → thumb(aspect=poster|banner|clearart|clearlogo|discart|landscape|keyart|squareart|logo)→ fanart(主图 + extrafanart)→ mpaa/certification → id(IMDb)+ tmdbid + uniqueid(默认 scraper 顺序 IMDB>TVDB>TMDB)→ country/premiered → watched/playcount/lastplayed → genre/studio/credits/director/tag/actor(含 person id 子元素)→ trailer(YT 转 `plugin://` URL)→ languages → showlink → dateadded → lockdata → fileinfo/streamdetails(codec 映射 avc→h264 等、resolution 1080|4K|8K、hdrtype、stereomode)→ flavor 特有 → 未识别标签原样回写 → TMM 块(source/edition/original_filename/user_note/english_title/crc32/crew/tmm_locked)。
- **flavor 差异**:Emby = Kodi + `set@tmdbcolid` + 多集 `episodenumberend` + credits/directors 移到 actors 之后 + Emby 额外 `<set>` 块保留;Jellyfin = Kodi + `collectionnumber`(TMDB set id,唯一识别途径);剧集 show/episode 的 Emby/Jellyfin 加 `enddate`;季 NFO 单一 flavor(`<season>` 根)。XBMC/MediaPortal 为 legacy,可忽略。
- **回读与用户数据保留**(JSoup 宽容解析):`MovieNfoParser` 2119 行 / `TvShowNfoParser` 2012 / `TvShowEpisodeNfoParser` 1873——每次重写前解析既有 NFO;`playcount/lastplayed/epbookmark` 取 DB 与 NFO 的 max/newer 合并;`userrating/watched/dateadded/user_note/original_filename/tmm_locked` 经 DB 往返;**一切未识别元素经 `addUnsupportedTags()` 字节级原样保留**(resume 就是这样存活的);仅电影 `lockdata` 故意丢弃。
- **无 Swing、无 license 门**;依赖仅为模块管理器 settings、MessageManager、实体模型——可整体作为行为规范移植。
- 顺带发现的两个上游 bug(移植时注意规避):episode trailer 写出被错误地由 `isNfoWriteAllActors()` 控制;`MovieToEmbyConnector.addSet()` 把对象 `toString()` 写进 `tmdbcolid`。

### 3.4 Artwork

- 类型全集在 `MediaFileType`:POSTER、FANART、BANNER、CLEARART、DISC、CLEARLOGO、THUMB、CHARACTERART、KEYART、SQUAREART、SEASON_POSTER/FANART/BANNER/THUMB、EXTRAFANART、EXTRATHUMB——**数据模型按此枚举开放,Phase 1 只实现 POSTER/FANART**。
- 下载原语 `ImageUtils.downloadImage`(278-378):临时 `.part` 文件 → 流式写 → 尺寸/零字节校验 → `Files.move` 原子替换 → 归一化 `.jpeg`→`.jpg`。**没有最小分辨率校验**(只有 extrafanart 挑选时 720p 过滤)——Lite 可以做得更好。
- 缓存 `ImageCache`(660 行):MD5(源路径/URL) 键 + 16 进制分片;透明像素 → PNG,否则 JPEG q=0.80。Web 版可简化(URL 磁盘缓存可选)。
- **选图排序 `sortArtworkUrls`**(MovieArtworkHelper 1132-1268,纯函数):语言+分辨率 → 非本地抓取按 likes 降序 → 空语言同分辨率(fanart 无文字版特判置顶)→ FFmpeg 本地检测图靠后 → 其他分辨率近优 → 兜底任意。纯函数 + (语言列表, sizeOrder, 3 个布尔) 注入,直接移植。
- 落盘文件名由 `IFileNaming` 枚举族(电影 21 个 + TV 20 个)决定:`poster.jpg`、`<basename>-poster.jpg` 等,用户配置哪套生效;**这就是 Kodi/Emby/Jellyfin 的文件名兼容规范,枚举本身就是文档**。

### 3.5 Renamer

- **模板引擎 JMTE** + 短 token 映射(`MovieRenamer.createTokenMap` 70+ 电影 token、TV 90+ token,完整表见 agent 报告附录);`${if:...}` 条件、chained renderer(`;number(%02d)`、`;date`、`;upper`)、`TmmOutputAppender` 把 token 输出中的路径分隔符替换为空格。
- **净化管线 `createDestination`**(战斗检验过):去空 `()[]{}`、折叠重复分隔符、token 边缘空白修剪、`...`→`…`、ASCII 音译、非法字符替换(冒号按设置)、Windows `"` 移除——**逐条照搬**。
- **Dry-run 是纯函数证明**:`MovieRenamerPreview`(107 行)克隆实体后只调 `createDestinationForFoldername / generateNewVideoBasename / generateFilename` 三个纯生成器 + 重复目标检测,零文件操作;执行器(`MovieRenameTask`)独立。Lite 照此分层:**planner(纯)与 executor(副作用)分离,dry-run = 只跑 planner**。
- **安全语义**:`moveFileSafe` 目标存在即抛异常(视频永不静默覆盖,失败中止整个改名);artwork/NFO 复制可覆盖(1:N);删除走 `deleteFileWithBackup`(数据源下备份目录);清理有"禁止删除数据源根/电影目录"守卫;**非事务**,崩溃可能留半完成状态(undo 靠改名末尾写入的 `MediaEntityFilenameHistory`)。
- `TvShowEpisodeAndSeasonParser`(1069 行):S01E02/堆叠/范围/1x02/13 种语言的 season 词/日期/罗马数字/动画前缀风格/纯数字启发式——**全 TMM 最自包含的正则状态机**,Phase 3 整体移植,以 TMM 现有行为为测试基准。

---

## 4. REUSE / REIMPLEMENT / IGNORE 清单

> REUSE = 按行为规范移植(必要处可参考 Apache-2.0 源码,保留许可声明);REIMPLEMENT = 只借鉴设计,重新实现;IGNORE = 不迁移。

### REUSE(黄金资产,Phase 1-3 逐条验收)

| 资产 | 来源(包/类/文件) | 移植目标 |
|---|---|---|
| NFO 元素规范 + flavor 差异 + 用户数据保留规则 | `core/movie/connector/MovieGenericXmlConnector`(1051)、`MovieToKodiConnector`(317)、`MovieToEmbyConnector`(106)、`MovieToJellyfinConnector`(43)、`MovieSetGenericXmlConnector`(620)、`core/tvshow/connector/TvShowGenericXmlConnector`(1171)、`TvShowEpisodeGenericXmlConnector`(1021)、`core/NfoUtils` | `internal/nfo`(Go 模板手写 XML,复刻元素序/属性/注释头/CRLF) |
| NFO 解析宽容规则 + 未识别标签透传 | `MovieNfoParser`(2119)、`TvShowEpisodeNfoParser`(1873)等 | `internal/nfo/parser` |
| TMDB 多语言/评分/artwork 逻辑 10 条 | `scraper/tmdb/TmdbMetadataProvider.getRequestLanguage/injectTranslations`、`TmdbTvShowMetadataProvider`(集列表 4 语言合并、EPISODE_TRANS、episode groups)、`TmdbArtworkProvider`(388)、`scraper/util/{MetadataUtil,Similarity,AdvancedSimilarity}`(~330 行纯数学) | `internal/scraper/tmdb` |
| Renamer token 表 + 净化管线 + naming 枚举 | `core/movie/MovieRenamer.createTokenMap/createDestination`、`core/jmte/*`、`core/{movie,tvshow}/filenaming/*`(41 个枚举) | `internal/renamer`(Go text/template 或轻量自研求值器) |
| 集/季文件名解析器 | `core/tvshow/TvShowEpisodeAndSeasonParser`(1069) | `internal/parser`(Phase 3) |
| Artwork 排序 + 下载原子性 | `MovieArtworkHelper.sortArtworkUrls`、`core/ImageUtils.downloadImage` | `internal/artwork` |
| 电影/剧集文件夹结构规范 | `MovieRenamer`/`TvShowRenamer` 的 folder/season 逻辑 | `internal/renamer` |

### REIMPLEMENT(设计可借鉴,代码纠缠不可搬)

| 目标 | 原因 | Lite 实现 |
|---|---|---|
| 实体模型 | `Movie` 等 7 类与模块管理器单例方法体级纠缠(§1、§2.1),且总量 >13k 行 | `internal/metadata`:瘦 struct + 明确 UUID 关系(照搬"存 id + 加载回链"形状) |
| 持久化 | H2 MVStore + JSON blob,无 schema、无查询能力 | SQLite(migrations 目录,§7 schema) |
| 任务系统 | `TmmTaskManager` 本质 = 1 主队列 + 扇出池,概念可留,实现不搬 | `internal/task`:内存队列 + SQLite 持久化状态 |
| 设置系统 | `AbstractSettings` 拖 ui 包 + jdesktop 依赖 | `internal/web` 设置页 + SQLite/JSON 单文件 |
| 匹配工作流 | `MovieScrapeTask` 的 smart-scrape 是 Swing 对话框守卫 | `internal/matcher`:纯函数打分 + REST 候选接口 + HTMX 确认页 |
| 实体级事件 | `SwingPropertyChangeSupport`(编译期 Swing) | 不需要;Web 轮询任务状态即可 |

### IGNORE

- `ui/` 全部 710 文件(Swing)、`updater/`(getdown)、`addon/`(Deno/yt-dlp 下载器)、`logging/`、`AppBundler/`、`native/`、`templates/`(导出模板)、`dbmigrator.jar`、`ant-getdown.xml`。
- `thirdparty/`:UPnP、KodiRPC、Trakt/Simkl、VSMeta、TinyFileDialogs(桌面)——Phase 4+ 再议。
- NFO 的 XBMC legacy + 3 个 MediaPortal connector;scraper 的 ofdb/fernsehserien/moviemeter/mpdbtv/thesportsdb/subdl/yify 等小众 provider;trailer/subtitle provider 族(Phase 4+)。
- 闭源 `org.tinymediamanager:license:5.3.1`(及其内嵌默认 API key)——法律红线,见 §5。

---

## 5. 法律与许可注意事项

1. 本仓库代码 **Apache-2.0**:可复用/修改/再分发,须保留许可与 NOTICE 声明;专利随代码授予。功能性再实现(干净代码)不受版权约束。
2. **闭源 license JAR 不得反编译/提取/再分发**;其中内嵌的默认 TMDB/fanart.tv 等 API key 是 TMM 的商业资产且受 TMDB API 服务条款约束(按注册应用授权)——**Media Manager Lite 必须注册自己的免费 key,由用户在 Web UI 填入**(这也正是需求 §6 的要求,与法律要求天然一致)。
3. 不使用 "tinyMediaManager" 名称/品牌/logo;不调用 `repo.tinymediamanager.org` 的 license/更新端点。
4. 若下游分发含 TMM 派生代码,注意 Apache-2.0 §6 的 NOTICE 传递。

---

## 6. 技术栈决策:Go(确认)

需求默认 Go,除非"Java 核心高度值得复用"。考古结论:**不成立**——

- 可复用的是行为规范(§4 REUSE),语言无关;可直接搬的 Java 代码(实体、helpers)全部绑着全局单例 + Swing 编译依赖 + H2 MVStore,剪出的"最小核心"实际 = 整个 core + scraper + JVM。
- 资源目标(空闲 <200MB):JVM 基线 + MVStore 缓存 + Jackson 即逼近上限,Go 单二进制空闲 <50MB。
- TMDB provider 60% 是薄映射;Retrofit services/entities ~2000 行在 Go 里 = 十几个 struct + net/http。
- SQLite(Go: `mattn/go-sqlite3` cgo 或 `modernc.org/sqlite` 纯 Go——**ARM64/Docker 交叉编译选纯 Go 驱动**)、HTTP(std lib)、模板(htm x + html/template)全部成熟。

**栈**:Go 1.23+ / `modernc.org/sqlite`(纯 Go,免 cgo 交叉编译)+ `net/http` + HTMX(无 npm 构建链)/ Docker buildx 多架构。

---

## 7. Media Manager Lite 架构设计

```
cmd/media-manager/            # 入口:serve 子命令 + CLI 子命令(scan/search/scrape/write-nfo/rename)
internal/
  scanner/                    # 目录遍历(防 traversal:Root FS 接口限定在 library path 内)
  parser/                     # 电影文件名解析(标题/年份/分辨率/编码/HDR/字幕/多集/堆叠)
  matcher/                    # 候选打分(移植 §3.2-6 评分) + 待确认队列
  scraper/
    tmdb/                     # client(endpoints §3.2)+ morph(翻译回退/集名过滤/episode groups)
  metadata/                   # 领域模型:Movie/TvShow/Season/Episode/MediaItem + ids map
  artwork/                    # 选图排序 + 下载(poster/fanart;类型枚举对齐 MediaFileType)
  nfo/                        # writer(三 flavor)+ parser(宽容读 + 未识别标签透传)
  renamer/                    # planner(纯函数,dry-run)+ executor(原子 move,冲突即中止)
  library/                    # Library/MediaItem 服务层(应用逻辑)
  database/                   # sqlite 连接 + migrations 嵌入(go:embed)
  task/                       # worker pool(可配置并发)+ 任务状态持久化
  web/                        # HTTP handlers + htmx 模板(html/template)
migrations/                   # SQL 迁移文件
web/                          # 静态模板/资产(htmx,无构建链)
docs/
```

**管道**(与需求 §四 一致,全部经 task 队列异步,HTTP 只入队/查询):

```
Scanner → Parser → Matcher(TMDB search + score) → [用户确认(MVP 必经)] → Scraper(details)
       → Metadata(入库) → Artwork(poster/fanart) → NFO Writer → Renamer(dry-run 默认)
```

**SQLite schema(Phase 1 最小集,Metadata 以 JSON 列存放避免过度设计):**

```sql
libraries(id TEXT PK, name TEXT, type TEXT /*movie|tvshow*/, path TEXT UNIQUE, created_at)
media_items(id TEXT PK, library_id →libraries, kind TEXT /*movie|episode*/,
            path TEXT, size INTEGER, file_ext TEXT,
            parsed_title, parsed_year, parsed_season, parsed_episode, parsed_date,
            status TEXT /*new|matched|unmatched|skipped*/, movie_id, episode_id, UNIQUE(library_id, path))
movies(id TEXT PK, /* tmdb_id/imdb_id/title/original_title/year/release_date/runtime/
        plot/tagline/rating REAL/votes/certification */ ..., metadata_json, art_json,
        nfo_status TEXT, artwork_status TEXT, library_id)
tv_shows(id TEXT PK, ...同上..., metadata_json)
seasons(id TEXT PK, tv_show_id, number, title, metadata_json)
episodes(id TEXT PK, tv_show_id, season_id, season_number, episode_number, ..., metadata_json)
tasks(id TEXT PK, type /*scan|search|scrape|artwork|nfo|rename*/, target_type, target_id,
      state /*pending|running|completed|failed*/, progress REAL, detail, error,
      created_at, started_at, finished_at)
settings(key TEXT PK, value)          -- 含 tmdb api key(Web UI 写入,API 侧永不明文回显/入日志)
```

**关键行为决策(源自 TMM 考古):**

- NFO 已存在时默认**询问 Skip/Update/Overwrite**(需求 §七)——对应 TMM `isWriteCleanNfo` 的经验:默认非 clean,合并保留用户数据(watched/playcount/dateadded/userrating/未识别标签透传)。
- 重命名默认 dry-run;视频 move 目标存在即失败中止,不静默覆盖;删除必须走备份目录;永不删除数据源根目录。
- API key 仅存 settings(应用启动后可整体加密文件权限 0600),日志脱敏。
- 外部请求统一 10-30s timeout + 429 Retry-After 处理(照 TmmHttp429RetryInterceptor 语义)。

---

## 8. MVP(Phase 1)实施计划

顺序即里程碑,每步有测试门禁;完成后进入 Phase 2(电影完整工作流)、Phase 3(剧集)、Phase 4(高级)。

1. **骨架**:go module、cmd 入口(serve/scan 子命令骨架)、config 加载、日志(zap/slog,含 key 脱敏中间件)、migrations 框架 + §7 schema、`make docker`(buildx amd64/arm64,基础镜像 `gcr.io/distroless/static` 或 alpine)。
   门禁:`docker compose up` 后 `/api/health` 200;空库迁移可重放。
2. **Library + Scanner + Parser**:添加/列出/删除 library(电影目录);遍历视频文件(扩展名白名单);电影文件名解析(`Movie.Name.2025.1080p.BluRay.x264.mkv` 级常见格式;分辨率/编码/HDR/多部分检测借鉴 TMM 常见 case);media_items 落库。
   门禁:表驱动单测(≥30 个真实样本名,含中英文、堆叠 CD1/CD2、多版本);traversal 拒绝用例。
3. **TMDB client**:§3.2 端点清单的 movie 部分(/configuration、/search/movie、/movie/{id}、/find);key 从 settings 读取;zh-CN 语言扩展 + 搜索级联 + 评分(移植 Similarity/AdvancedSimilarity ~330 行数学 + 年份罚);429/timeout;httptest 单测 + 录制 fixture。
   门禁:已知片名搜索 top1 命中(含中文片名);错误 key 返回结构化错误;日志无 key。
4. **Matcher + 确认流**:`POST /api/movies/{id}/search` 返回候选(score 排序,附 poster 缩略 URL);HTMX 页面展示"待匹配文件 → 候选 → 确认";确认后 `POST .../scrape` 拉详情(翻译回退链)入库。
   门禁:匹配/不匹配两路径的集成测试(用 VCR 式 fixture,不打真 API)。
5. **Artwork + NFO + 任务队列**:poster/fanart 下载(原子 .part + 校验,`poster.jpg`/`fanart.jpg` + TMM naming 兼容选项);NFO writer(Kodi/Emby/Jellyfin 三 flavor,元素序按 §3.3);已存在 NFO → Skip/Update/Overwrite;后台任务队列 + `/api/tasks` + 任务页。
   门禁:**NFO 黄金文件测试**——以 TMM 实际生成的 NFO 为 fixture,断言 Lite 输出被 Jellyfin/Emby 关键字段解析等价(uniqueid/ratings/set/fileinfo/collectionnumber);用户数据保留用例(预置含 resume/未识别标签的 NFO,Update 后原样存活)。
6. **Rename(dry-run 默认)+ Dashboard**:planner/executor 分层;`Movie Name (Year)/Movie Name (Year).ext` 模板;`?dry_run=true` 返回 old→new 计划;executor 原子 move + 冲突中止;首页 Dashboard(库统计/任务状态/未匹配数)。
   门禁:rename 集成测试(临时目录,含"目标已存在必须失败且无副作用"用例);dry-run 零写操作断言。
7. **Docker/ARM64 收口**:compose 示例(config + media 卷挂载)、/healthz、内存压测(空闲 <200MB 验收)、交叉编译 CI。

**Phase 2 扩展**:批量刮削、手动改匹配、artwork 缺失补全、NFO 重写策略细化、CLI 子命令对齐(需求 §十二)。
**Phase 3 前置**:移植 `TvShowEpisodeAndSeasonParser`(1069 行,以 TMM 行为为基准测试)+ 季/集 NFO(`tvshow.nfo`、`season%02d.nfo`、多集拼接 `<episodedetails>`)+ EPISODE_TRANS 词表 + episode groups。
**Phase 4**:TVDB/FanartTV provider 接口位(设计时就按 provider 接口留桩)、字幕、合集、多版本。

---

## 9. 风险清单

| 风险 | 缓解 |
|---|---|
| NFO 兼容性回归 | 黄金文件 fixture + 与 TMM 输出 diff 测试;遵循"未识别标签透传"不变式 |
| 中文匹配质量(评分算法对 CJK) | StrikeAMatch 按 UTF-16 对验证;必要时 zh 场景提高 Jaro-Winkler 权重;留手动确认兜底(MVP 已有) |
| TMDB 限流 | 429 Retry-After + 响应缓存(15min 语义)+ 批处理合并请求 |
| SQLite 并发写 | 单写者(WAL);任务队列串行写入口 |
| ARM64 交叉编译 | 纯 Go SQLite 驱动(`modernc.org/sqlite`);distroless 无需 libc 适配 |
| 范围蔓延 | 严格按 §8 里程碑;任何 Phase 4 功能进入 Phase 1 即拒绝 |
