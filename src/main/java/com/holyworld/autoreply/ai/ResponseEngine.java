package com.holyworld.autoreply.ai;

import com.holyworld.autoreply.HolyWorldAutoReply;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class ResponseEngine {

    private final ConcurrentHashMap<String, PlayerState> states = new ConcurrentHashMap<>();
    private final List<Rule> rules = new ArrayList<>();

    public ResponseEngine() {
        buildRules();
        rules.sort((a, b) -> Integer.compare(b.priority, a.priority));
        HolyWorldAutoReply.LOGGER.info("[Engine] Загружено {} правил", rules.size());
    }

    // ======================== STATE ========================

    public static class PlayerState {
        public long startTime = System.currentTimeMillis();
        public int msgCount = 0;
        public boolean sentGreeting = false;
        public boolean askedForAnydesk = false;
        public boolean gaveCodes = false;
        public boolean mentionedRustdesk = false;
        public boolean warnedRefusal = false;
        public boolean warnedConfession = false;
        public boolean offeredConfession = false;
        public boolean warnedTime = false;
        // Контекст: мы уже попросили скачать AnyDesk и ждём ответа
        public boolean awaitingAnydeskResponse = false;
        public String lastCat = "";

        public int remaining() {
            return Math.max(1, 7 - (int)((System.currentTimeMillis() - startTime) / 60000));
        }

        public long elapsed() {
            return (System.currentTimeMillis() - startTime) / 60000;
        }
    }

    // ======================== RESULT ========================

    public static class Result {
        public enum Action { REPLY, BAN_INSULT, BAN_REFUSAL, BAN_CONFESSION }
        public final Action action;
        public final String message;

        private Result(Action a, String m) { this.action = a; this.message = m; }

        public static Result reply(String m) { return new Result(Action.REPLY, m); }
        public static Result banInsult() { return new Result(Action.BAN_INSULT, null); }
        public static Result banRefusal() { return new Result(Action.BAN_REFUSAL, null); }
        public static Result banConfession() { return new Result(Action.BAN_CONFESSION, null); }
    }

    // ======================== RULE ========================

    @FunctionalInterface private interface Match { boolean test(String m, String l, PlayerState s); }
    @FunctionalInterface private interface Reply { Result get(String m, String l, PlayerState s); }

    private static class Rule {
        final String cat; final int priority; final Match match; final Reply reply;
        Rule(String c, int p, Match m, Reply r) { cat=c; priority=p; match=m; reply=r; }
    }

    // ======================== HELPERS ========================

    private static String pick(String... o) { return o[ThreadLocalRandom.current().nextInt(o.length)]; }

    private static boolean has(String t, String... kw) {
        for (String k : kw) if (t.contains(k)) return true;
        return false;
    }

    private static boolean eq(String t, String... vals) {
        String tr = t.trim();
        for (String v : vals) if (tr.equals(v)) return true;
        return false;
    }

    // ======================== BUILD RULES ========================

    private void buildRules() {

        // ==================== 500: ПРЯМОЙ БАН (точные слова) ====================

        rules.add(new Rule("ban_refusal_exact", 500,
            (m,l,s) -> eq(l, "отказ", "отказываюсь"),
            (m,l,s) -> Result.banRefusal()));

        rules.add(new Rule("ban_confession_exact", 500,
            (m,l,s) -> eq(l, "я чит", "я читер", "признание"),
            (m,l,s) -> Result.banConfession()));

        // ==================== 400: ОСКОРБЛЕНИЯ МОДЕРАТОРА ====================
        // Бан ТОЛЬКО если оскорбляет МОДЕРАТОРА (тебя/ты/модер)

        rules.add(new Rule("insult_moderator", 400,
            (m,l,s) -> {
                // Проверяем что есть мат
                boolean hasMat = has(l,
                    "нахуй","нахуи","пошел нах","пошёл нах","иди нах",
                    "хуй","хуи","хуе","хуё","хуесос",
                    "ебал","ебан","ебат","ебу","ёба","ебаш",
                    "сука","суки","сучка","блядь","бляд","блять",
                    "далбаеб","долбаеб","долбоеб","дебил",
                    "мразь","урод","гандон","гондон",
                    "пидор","пидр","педик",
                    "чмо","чмошник",
                    "соси","пизд","шлюха","тварь",
                    "выродок","ублюд","конч","даун","кретин",
                    "идиот","мусор","шакал","крыса","скотина",
                    "уебок","уёбок","уебан","залупа",
                    "хуйня","ебучий","ебучая","сучий",
                    "говно","говнюк","говнарь",
                    "мудак","мудила","козёл","козел");

                if (!hasMat) return false;

                // Проверяем что оскорбление направлено на модератора
                boolean directedAtMod = has(l,
                    "ты ","тебя ","тебе ","тобой ","твою ","твоя ","твой ",
                    "модер","админ","проверяющ","ты,","тебя,",
                    "пошел ","иди ","соси","катись",
                    "ты сука","ты мразь","ты урод","ты дебил",
                    "ты даун","ты чмо","ты пидор","ты конч",
                    "модер сука","модер дебил","модер даун");

                // Если мат + направлено на модера = бан
                // Если просто мат без обращения к модеру (ругается в воздух) -
                // тоже бан, потому что это в чате проверки
                // Но если мат в контексте "я с софтом блять" - это не оскорбление модера
                boolean justSwearing = has(l,
                    "блять скачаю","блять ладно","блять ок",
                    "блять щас","сука скачаю","сука ладно",
                    "блин","бляха","ёпт","епт","чёрт","черт");

                if (justSwearing && !directedAtMod) return false;

                return directedAtMod || hasMat;
            },
            (m,l,s) -> Result.banInsult()));

        // ==================== 350: КОНТЕКСТНЫЙ ОТКАЗ ====================
        // Если мы попросили скачать AnyDesk, а игрок говорит "нет" / "не хочу" / "не буду"

        rules.add(new Rule("contextual_refusal", 350,
            (m,l,s) -> {
                if (!s.askedForAnydesk) return false;
                // Игрок отвечает "нет" на просьбу скачать
                return eq(l, "нет", "не", "неа", "нее", "не буду", "нет спасибо", "нет,", "нет.") ||
                    has(l, "не хочу","не буду","не стану","не собираюсь",
                        "мне лень","я отказываюсь от","не буду скачивать",
                        "не буду качать","не хочу скачивать","не хочу качать",
                        "не буду проходить","не хочу проходить","забей",
                        "не буду ничего","забань просто","бань уже",
                        "давай бан","мне пофиг","мне похер",
                        "похуй на бан","плевать на бан","мне все равно",
                        "мне всё равно","забань и всё","не обязан",
                        "я отказ","отвали","не буду я");
            },
            (m,l,s) -> {
                if (!s.warnedRefusal) {
                    s.warnedRefusal = true;
                    return Result.reply("Если отказываешься от проверки - напиши \"Отказ\". Это бан на 30 дней. Проверка занимает 2 минуты, подумай.");
                }
                return Result.reply("Последний шанс. Напиши \"Отказ\" если точно не хочешь проходить проверку.");
            }));

        // ==================== 300: КОНТЕКСТНОЕ ПРИЗНАНИЕ ====================
        // Игрок говорит что у него софт / называет читы

        rules.add(new Rule("contextual_confession", 300,
            (m,l,s) -> has(l,
                "я с софтом","у меня софт","у меня читы","да я читер",
                "у меня есть софт","да я софтер","я с читами",
                "ладно я с читами","я юзаю","я использую",
                "у меня x-ray","у меня xray","у меня иксрей",
                "у меня селестиал","у меня celestial",
                "у меня impact","у меня импакт",
                "у меня wurst","у меня вурст",
                "у меня meteor","у меня метеор",
                "у меня rusherhack","у меня рашерхак",
                "у меня aristois","у меня аристоис",
                "у меня baritone","у меня баритон",
                "у меня killaura","у меня киллаура",
                "у меня aimbot","у меня аимбот",
                "у меня autoclicker","у меня автокликер",
                "у меня reach","у меня рич",
                "у меня fly","у меня флай",
                "у меня speed","у меня спид",
                "у меня nuker","у меня нукер",
                "у меня freecam","у меня фрикам",
                "я с модом на читы","у меня чит клиент",
                "у меня читерский","у меня запрещённый",
                "у меня запрещенный",
                "с софтом бань","хочешь бань","хочеш бань",
                "бань нахуй ладно","ладно я софт",
                "окей я читер","ок я читер",
                "хорошо я признаюсь","я признаюсь",
                "я признаюсь в","признаюсь что",
                "готов признаться","хочу признаться","могу признаться",
                "го признание","давай признание",
                "давай признаюсь","ладно признаюсь"),
            (m,l,s) -> {
                if (!s.warnedConfession) {
                    s.warnedConfession = true;
                    return Result.reply("Для официального признания напиши \"Я чит\". Срок бана уменьшится до 20 дней вместо 30.");
                }
                return Result.reply("Напиши \"Я чит\" для оформления признания. Бан будет 20 дней.");
            }));

        // ==================== 250: УГРОЗЫ ====================

        rules.add(new Rule("threats", 250,
            (m,l,s) -> has(l,
                "найду тебя","тебе конец","ты труп","убью","зарежу",
                "взломаю","ддос","ddos","деаноним","сват","свачу",
                "узнаю где жив","я тебя найд"),
            (m,l,s) -> Result.reply(pick(
                "Угрозы только ухудшают твоё положение. Качай AnyDesk и проходи проверку.",
                "Мне не страшно, а вот тебе стоит поторопиться. Жду AnyDesk.",
                "Угрозы не отменяют проверку. Осталось " + s.remaining() + " мин."
            ))));

        // ==================== 200: УХОД ====================

        rules.add(new Rule("leaving", 200,
            (m,l,s) -> {
                String t = l.trim();
                return eq(t, "бб", "bb", "бай", "пока", "выхожу") ||
                    has(l, "я ухожу","я выхожу","я выйду","я ливаю","я ливну",
                        "бб всем","бб короче","пока всем","качать не охота",
                        "качать не буду","я жду бан","лад баньте","ладно баньте");
            },
            (m,l,s) -> {
                if (!s.warnedRefusal) {
                    s.warnedRefusal = true;
                    return Result.reply("Выход = отказ от проверки (30 дней бана). Если уверен - напиши \"Отказ\". Проверка занимает 2 минуты.");
                }
                return Result.reply("Уход = автоматический отказ. Напиши \"Отказ\" или останься и пройди проверку.");
            }));

        // ==================== 150: КОД ====================

        rules.add(new Rule("code", 150,
            (m,l,s) -> m.replaceAll("[^0-9]", "").length() >= 9,
            (m,l,s) -> {
                s.gaveCodes = true;
                s.awaitingAnydeskResponse = false;
                return Result.reply(pick(
                    "Код принят. Подключаюсь. Прими запрос в AnyDesk.",
                    "Принимай запрос подключения. Не трогай мышку и клавиатуру.",
                    "Вижу код. Подключаюсь, нажми зелёную кнопку 'Принять'.",
                    "Сейчас зайду. Жми 'Принять' когда появится запрос."
                ));
            }));

        // ==================== 145: СКИНУЛ В ЛС ====================

        rules.add(new Rule("sent_pm", 145,
            (m,l,s) -> has(l,
                "в лс скинул","скинул в лс","написал в лс","кинул в лс",
                "отправил в лс","проверь лс","чекни лс","гляди лс",
                "в личку","в лс написал","в лс кинул"),
            (m,l,s) -> {
                s.gaveCodes = true;
                return Result.reply(pick(
                    "Вижу. Подключаюсь, прими запрос.",
                    "Принял, жди подключения. Не трогай мышку.",
                    "Сейчас зайду. Нажми 'Принять'."
                ));
            }));

        // ==================== 140: ДИСКОРД / ВК / ТГ ====================

        rules.add(new Rule("discord", 140,
            (m,l,s) -> has(l,
                "через дс","давай дс","го дс","можно дс","по дс",
                "через дискорд","можно дискорд","го дискорд",
                "могу дс","в звонок","го в звонок",
                "скайп","тимспик","зум","zoom"),
            (m,l,s) -> Result.reply(pick(
                "Нет, проверка только через AnyDesk. Это стандартная процедура сервера.",
                "Через дискорд проверки не проводятся. Качай AnyDesk - anydesk com.",
                "Только AnyDesk или RustDesk. Другие программы не принимаются."
            ))));

        rules.add(new Rule("vk_tg", 138,
            (m,l,s) -> has(l, "через вк","го вк","можно вк","через тг","го тг","можно тг",
                "демонстрация","стрим","трансляция","могу показать экран"),
            (m,l,s) -> Result.reply("Только через AnyDesk. Стримы и демонстрации не принимаются.")));

        // ==================== 135: КОМУ КОД ====================

        rules.add(new Rule("to_pm", 135,
            (m,l,s) -> has(l, "можно в лс","могу в лс","кому в лс","куда кидать",
                "кому скинуть","куда скинуть","кому код","куда код"),
            (m,l,s) -> Result.reply("Скинь код мне в личные сообщения (ЛС).")));

        // ==================== 130: ЗА ЧТО ПРОВЕРКА ====================

        rules.add(new Rule("reason", 130,
            (m,l,s) -> has(l,
                "за что","причина","за что прове","почему вызвал",
                "за что вызвал","почему меня","что я сделал",
                "что случилось","в чем причина","зачем вызвал",
                "за что проверка","что произошло","почему проверка",
                "я ничего не делал","я не нарушал","за что меня",
                "что я зделал","а щас то за что"),
            (m,l,s) -> {
                s.askedForAnydesk = true;
                s.awaitingAnydeskResponse = true;
                return Result.reply(pick(
                    "На тебя поступили жалобы от игроков. Это стандартная процедура. Качай AnyDesk - anydesk com.",
                    "Причина проверки - репорты. Скачивай AnyDesk (anydesk com) и пройди проверку.",
                    "Жалобы на подозрительную игру. Качай AnyDesk, докажи что чист.",
                    "Репорты. Не трать время на вопросы - качай AnyDesk с anydesk com."
                ));
            }));

        // ==================== 128: Я ПРОСТО ИГРАЛ / КОПАЛСЯ ====================

        rules.add(new Rule("just_playing", 128,
            (m,l,s) -> has(l,
                "я просто играл","я просто копал","я просто строил",
                "я просто бегал","я ничего не делал такого",
                "я просто фармил","я просто стоял","я афк был",
                "я был на спавне","я на спавне","я возле дома",
                "я тока зашёл","я только зашел","я только что зашёл",
                "я тут недавно","я пару минут играю",
                "я просто ходил","я просто крафтил","я просто стреляю"),
            (m,l,s) -> {
                s.askedForAnydesk = true;
                s.awaitingAnydeskResponse = true;
                return Result.reply(pick(
                    "Проверка не значит что ты виноват. Это стандартная процедура. Качай AnyDesk и вернёшься к игре через 2 минуты.",
                    "Понимаю, но на тебя пожаловались. Пройди проверку - если чист, быстро вернёшься к игре.",
                    "Ничего страшного. Скачай AnyDesk (anydesk com), покажи что софта нет - и свободен."
                ));
            }));

        // ==================== 126: КАКИЕ ЧИТЫ ====================

        rules.add(new Rule("what_cheats", 126,
            (m,l,s) -> has(l,
                "какие читы","что за читы","какой чит","какой софт",
                "что за софт","какие жалобы","на что жалобы",
                "в чём подозревают","в чем подозревают",
                "а что именно","что конкретно","какие репорты",
                "на что репорт","на что написали","за что жалоба"),
            (m,l,s) -> {
                s.askedForAnydesk = true;
                s.awaitingAnydeskResponse = true;
                return Result.reply(pick(
                    "Модератор не обязан раскрывать детали репортов. Качай AnyDesk и проходи проверку.",
                    "Подробности не разглашаются. Проверка покажет всё сама. Жду AnyDesk.",
                    "Информация конфиденциальна. Скачивай AnyDesk (anydesk com) и пройди проверку."
                ));
            }));

        // ==================== 124: Я НЕ ЧИТЕР ====================

        rules.add(new Rule("not_cheater", 124,
            (m,l,s) -> has(l,
                "я не читер","я не читар","я не софт",
                "я чист","у меня нет читов","без читов",
                "без софта","я легит","я не юзаю","я легитный",
                "я чистый","клянусь","честное слово","зуб даю",
                "мамой клянусь","богом клянусь","я 100%",
                "у меня нет ничего","я готов пройти"),
            (m,l,s) -> {
                s.askedForAnydesk = true;
                s.awaitingAnydeskResponse = true;
                return Result.reply(pick(
                    "Отлично! Тогда тебе нечего бояться. Скачивай AnyDesk и быстро пройдёшь проверку.",
                    "Если чист - проверка займёт пару минут. Качай AnyDesk с anydesk com.",
                    "Верю! Но нужна проверка. Скачивай AnyDesk, покажи что всё чисто.",
                    "Раз без читов - докажи это. AnyDesk, две минуты, и свободен."
                ));
            }));

        // ==================== 122: ЧТО БУДЕШЬ СМОТРЕТЬ ====================

        rules.add(new Rule("what_check", 122,
            (m,l,s) -> has(l,
                "что будешь смотреть","что ты будешь смотреть",
                "что проверяешь","что будешь проверять",
                "что именно смотр","а что смотришь","куда смотришь",
                "что ты ищешь","что надо проверить",
                "куда будешь лезть","что будешь искать"),
            (m,l,s) -> Result.reply(pick(
                "Проверю рабочий стол, процессы, папку с игрой на наличие запрещённого ПО.",
                "Посмотрю процессы, файлы Minecraft и недавние загрузки. Ничего личного не трогаю.",
                "Стандартная проверка: процессы, файлы Minecraft, рабочий стол. Это быстро."
            ))));

        // ==================== 120: ЧТО ТАКОЕ ANYDESK ====================

        rules.add(new Rule("what_anydesk", 120,
            (m,l,s) -> has(l,
                "что за аник","что такое аник","что за анидеск",
                "что такое анидеск","что за прога","че за прога",
                "анидеск это что","а зачем скачивать",
                "это безопасно","это вирус","он опасн"),
            (m,l,s) -> {
                if (has(l, "вирус", "опасн", "безопасн"))
                    return Result.reply("AnyDesk - официальная безопасная программа. Используется миллионами людей. Сайт anydesk com.");
                return Result.reply(pick(
                    "AnyDesk - программа удалённого доступа. Через неё я проверю ПК на наличие читов. Безопасно, anydesk com.",
                    "Программа для удалённого просмотра экрана. Официальная, безопасная. Качай с anydesk com."
                ));
            }));

        // ==================== 118: ГДЕ СКАЧАТЬ ====================

        rules.add(new Rule("where_dl", 118,
            (m,l,s) -> has(l,
                "где скачать","как скачать","ссылку","ссылка",
                "откуда качать","откуда скачать","как установить",
                "где найти","хз где","не знаю как",
                "что скачать","что качать","какую программу",
                "что надо скачать","помоги скачать"),
            (m,l,s) -> {
                s.askedForAnydesk = true;
                s.awaitingAnydeskResponse = true;
                return Result.reply(pick(
                    "Открой браузер, набери anydesk com. Скачай, запусти, скинь мне код (цифры) в ЛС.",
                    "Сайт: anydesk com. Скачиваешь, открываешь, кидаешь мне ID в личные сообщения.",
                    "В браузере пиши anydesk com - скачивай, запускай, скинь мне Ваш ID в ЛС."
                ));
            }));

        // ==================== 116: ГДЕ КОД ====================

        rules.add(new Rule("where_code", 116,
            (m,l,s) -> has(l, "где код","где id","где айди","где цифры","не вижу код","не могу найти код","какой код"),
            (m,l,s) -> Result.reply("Код находится в AnyDesk при запуске - 'Ваш адрес' или 'Your ID'. Длинное число, скинь его мне в ЛС.")));

        // ==================== 114: СКАЧИВАЕТ ====================

        rules.add(new Rule("downloading", 114,
            (m,l,s) -> has(l,
                "скачиваю","качаю","загружаю","устанавливаю",
                "щас скачаю","ща скачаю","уже качаю","жди качаю",
                "грузится","загружается","качается"),
            (m,l,s) -> {
                s.awaitingAnydeskResponse = false;
                return Result.reply(pick(
                    "Жду. Осталось " + s.remaining() + " мин.",
                    "Хорошо, не затягивай. Осталось " + s.remaining() + " мин.",
                    "Ок, жду. Как скачаешь - кидай код мне в ЛС."
                ));
            }));

        // ==================== 112: СКАЧАЛ ====================

        rules.add(new Rule("downloaded", 112,
            (m,l,s) -> has(l,
                "скачал","загрузил","установил","скачался",
                "загрузился","готово","всё скачал","уже открыл",
                "запустил","открыл"),
            (m,l,s) -> {
                s.awaitingAnydeskResponse = false;
                return Result.reply(pick(
                    "Отлично! Найди свой код (Ваш адрес / Your ID) и скинь мне в ЛС.",
                    "Супер! Открывай и кидай длинное число мне в личные сообщения.",
                    "Теперь найди цифры (ID) и скинь их мне в ЛС."
                ));
            }));

        // ==================== 110: НЕ СКАЧИВАЕТСЯ ====================

        rules.add(new Rule("cant_dl", 110,
            (m,l,s) -> has(l,
                "не скачивается","не качается","не загружается",
                "не грузит","не могу скачать","не работает",
                "ошибка","не запускается","не открывается",
                "не получается","заблокирован","блокирует","антивирус","зависло"),
            (m,l,s) -> {
                if (!s.mentionedRustdesk) {
                    s.mentionedRustdesk = true;
                    return Result.reply("Попробуй RustDesk - аналог AnyDesk, работает везде. Сайт: rustdesk com.");
                }
                return Result.reply(pick(
                    "Попробуй другой браузер или отключи антивирус. Или RustDesk (rustdesk com).",
                    "Всё должно работать. Попробуй ещё раз. Осталось " + s.remaining() + " мин."
                ));
            }));

        // ==================== 108: ТЕЛЕФОН ====================

        rules.add(new Rule("phone", 108,
            (m,l,s) -> has(l, "с телефон","на телефоне","с мобил","на андроид","с планшета","с айфона"),
            (m,l,s) -> Result.reply("AnyDesk есть на телефон! Скачай из Play Market или App Store.")));

        // ==================== 106: RUSTDESK ====================

        rules.add(new Rule("rustdesk", 106,
            (m,l,s) -> has(l, "растдеск","rustdesk","рустдеск","можно рустдеск","подойдет рустдеск","можно раст"),
            (m,l,s) -> { s.mentionedRustdesk = true; return Result.reply("Да, RustDesk подойдёт! Качай с rustdesk com."); }));

        // ==================== 104: ИЗ РФ ====================

        rules.add(new Rule("rf", 104,
            (m,l,s) -> has(l, "из рф","из россии","заблокирован в рф","в рф не работает","запрещен в рф"),
            (m,l,s) -> { s.mentionedRustdesk = true; return Result.reply("Качай RustDesk - работает в РФ без VPN. Сайт: rustdesk com."); }));

        rules.add(new Rule("vpn", 103, (m,l,s) -> has(l, "впн", "vpn"), (m,l,s) -> Result.reply("Качай RustDesk - работает без VPN. rustdesk com.")));

        // ==================== 100: ВОПРОСЫ О БАНЕ ====================

        rules.add(new Rule("ban_questions", 100,
            (m,l,s) -> has(l,
                "какое признание","что за признание","сколько бан",
                "на сколько забанят","какой бан","сколько дней",
                "а если признаюсь","что будет если признаюсь",
                "какое наказание","на скок бан"),
            (m,l,s) -> {
                s.offeredConfession = true;
                return Result.reply("Признание = 20 дней бана. Отказ = 30 дней. Если хочешь признаться - напиши \"Я чит\". Если отказаться - \"Отказ\".");
            }));

        // ==================== 90: ПРОСТЫЕ ОТВЕТЫ ====================

        rules.add(new Rule("nah_no", 90,
            (m,l,s) -> {
                // Простое "нет" без контекста AnyDesk
                if (s.awaitingAnydeskResponse) return false; // обрабатывается contextual_refusal
                return eq(l, "нет", "не", "неа", "нее");
            },
            (m,l,s) -> Result.reply("Что нет? Скачивай AnyDesk или напиши \"Отказ\" если не хочешь проходить проверку.")));

        rules.add(new Rule("wait", 88,
            (m,l,s) -> eq(l, "ща","щас","сек","минуту","минутку","подожди","секунду") ||
                has(l, "подожд","погод","чуть чуть","пару минут","пару секунд","дай секунд"),
            (m,l,s) -> Result.reply(pick("Жду. Осталось " + s.remaining() + " мин.", "Ок, давай быстрее.", "Хорошо, не затягивай."))));

        rules.add(new Rule("yes", 86,
            (m,l,s) -> eq(l, "да","+","ок","окей","ладно","хорошо","понял","понятно","ясно","угу","ага","пон","лан"),
            (m,l,s) -> {
                s.awaitingAnydeskResponse = false;
                if (!s.askedForAnydesk) { s.askedForAnydesk = true; return Result.reply("Качай AnyDesk с anydesk com. Осталось " + s.remaining() + " мин."); }
                if (s.gaveCodes) return Result.reply("Принимай запрос!");
                return Result.reply(pick("Жду.", "Давай.", "+"));
            }));

        rules.add(new Rule("time", 84,
            (m,l,s) -> has(l, "сколько времени","сколько минут","сколько осталось","скок времени","скок осталось"),
            (m,l,s) -> Result.reply("Осталось " + s.remaining() + " минут.")));

        rules.add(new Rule("qmarks", 82, (m,l,s) -> l.trim().matches("[?!?!]+"),
            (m,l,s) -> { if (s.msgCount <= 2) return Result.reply("Проверка на читы. Скачивай AnyDesk с anydesk com. У тебя 7 минут."); return Result.reply("Жду AnyDesk."); }));

        // ==================== 80: РАЗНОЕ ====================

        rules.add(new Rule("what_next", 80, (m,l,s) -> has(l, "что дальше","что делать","что мне делать","куда жмать","а дальше","дальше что","куда нажать","я не понимаю"),
            (m,l,s) -> { if (s.gaveCodes) return Result.reply("Нажми зелёную кнопку 'Принять' в AnyDesk. Не трогай мышку."); if (s.askedForAnydesk) return Result.reply("Кидай код (длинное число из AnyDesk) мне в ЛС."); return Result.reply("1. Зайди на anydesk com 2. Скачай 3. Открой 4. Скинь код в ЛС 5. Прими запрос"); }));

        rules.add(new Rule("accepted", 78, (m,l,s) -> has(l, "принял","я принял","нет кнопки","не пришло","не приходит","нет запроса","где кнопка","от кого"),
            (m,l,s) -> { if (has(l, "как принять","нет кнопки","где кнопка")) return Result.reply("В AnyDesk появится окно. Нажми зелёную кнопку 'Принять'."); if (has(l, "не пришло","не приходит","нет запроса")) return Result.reply("Скинь код ещё раз. Убедись что AnyDesk запущен."); return Result.reply("Хорошо, не трогай мышку и клавиатуру. Идёт проверка."); }));

        rules.add(new Rule("no_anik", 76, (m,l,s) -> has(l, "нету аник","нет аник","аника нет","анидеска нет","нету программы","не установлен"),
            (m,l,s) -> { s.askedForAnydesk = true; s.awaitingAnydeskResponse = true; return Result.reply("Скачивай с сайта anydesk com. Осталось " + s.remaining() + " мин."); }));

        rules.add(new Rule("reg", 74, (m,l,s) -> has(l, "регистрац","регаться","просит регистрацию"), (m,l,s) -> Result.reply("Регистрация не нужна. Просто скачай, открой и скинь код.")));
        rules.add(new Rule("paid", 72, (m,l,s) -> has(l, "платная","платный","бесплатн","стоит денег","просит оплату"), (m,l,s) -> Result.reply("AnyDesk бесплатный для личного использования.")));
        rules.add(new Rule("delete", 70, (m,l,s) -> has(l, "потом удалить","можно удалить","удалю после"), (m,l,s) -> Result.reply("Да, после проверки можешь удалить.")));
        rules.add(new Rule("size", 68, (m,l,s) -> has(l, "сколько весит","много весит"), (m,l,s) -> Result.reply("AnyDesk весит около 5 МБ.")));
        rules.add(new Rule("plugin", 66, (m,l,s) -> has(l, "плагин","plugin","ad1","полный доступ","нет доступа"), (m,l,s) -> Result.reply("В AnyDesk: три линии слева сверху -> Настройки -> Плагин AD1 -> Активировать.")));
        rules.add(new Rule("english_ui", 64, (m,l,s) -> has(l, "на англ","английском","не понимаю англ"), (m,l,s) -> Result.reply("Просто найди длинное число (ID) и скинь мне.")));
        rules.add(new Rule("legal", 62, (m,l,s) -> has(l, "не законно","незаконно","не имеете права","мои права","персональные данные"), (m,l,s) -> Result.reply("Заходя на сервер ты принимаешь правила. Проверка обязательна.")));
        rules.add(new Rule("trust", 60, (m,l,s) -> has(l, "не доверяю","родительский контроль"), (m,l,s) -> { if (has(l,"родительский")) return Result.reply("Попроси разрешение у родителей."); return Result.reply("AnyDesk можно отключить в любой момент. Я только посмотрю папки."); }));
        rules.add(new Rule("weak_pc", 58, (m,l,s) -> has(l, "слабый пк","слабый комп","медленно","лагает","тормозит"), (m,l,s) -> Result.reply("AnyDesk весит 5 МБ, работает на любом ПК.")));
        rules.add(new Rule("prev_check", 56, (m,l,s) -> has(l, "меня проверяли","уже проверяли","вчера проверяли"), (m,l,s) -> Result.reply("Повторная проверка - нормальная практика. Качай AnyDesk.")));
        rules.add(new Rule("friend", 54, (m,l,s) -> has(l, "это мой друг","это мой брат","играет брат","это не я","играл не я"), (m,l,s) -> Result.reply("Ответственность на владельце аккаунта. Качай AnyDesk.")));
        rules.add(new Rule("mods", 52, (m,l,s) -> has(l, "у меня моды","у меня мод","оптифайн","шейдер"), (m,l,s) -> Result.reply("Разрешённые моды - не проблема. Качай AnyDesk.")));
        rules.add(new Rule("bribe", 50, (m,l,s) -> has(l, "ресы","деньги отдам","могу заплатить","дам денег","кину донат"), (m,l,s) -> Result.reply("Нет. Проверка обязательна и не отменяется.")));
        rules.add(new Rule("stalling", 48, (m,l,s) -> has(l, "давай потом","давай завтра","можно завтра","давай позже","не сейчас","давай поболтаем"), (m,l,s) -> Result.reply("Проверка проводится сейчас. Потом нельзя.")));
        rules.add(new Rule("minimap", 46, (m,l,s) -> has(l, "миникарта","минимап"), (m,l,s) -> Result.reply("Миникарта разрешена.")));
        rules.add(new Rule("conn", 44, (m,l,s) -> has(l, "не подключается","клиент не в сети","не коннектит","отключился","вылетел"), (m,l,s) -> Result.reply("Скинь код ещё раз. Или качай RustDesk (rustdesk com).")));
        rules.add(new Rule("busy", 42, (m,l,s) -> has(l, "на работе","на уроке","в школе","на паре","я занят","мне некогда"), (m,l,s) -> Result.reply("Проверка обязательна. Осталось " + s.remaining() + " мин. Можешь качать с телефона.")));

        rules.add(new Rule("here", 40, (m,l,s) -> has(l, "ты тут","ты здесь","ало","ау","модер","але","эй"),
            (m,l,s) -> Result.reply(pick("Да, я на месте. Жду AnyDesk.", "Я здесь. Качай AnyDesk, время идёт."))));

        rules.add(new Rule("emotional", 38, (m,l,s) -> eq(l.trim(), "хаха","хахаха","ахахах","xd","лол","кек","ору") || l.trim().matches("[)(]+"),
            (m,l,s) -> Result.reply("Время идёт. Жду AnyDesk.")));

        rules.add(new Rule("done", 36, (m,l,s) -> has(l, "спасибо","спс","я прошел","я прошёл","благодарю"),
            (m,l,s) -> Result.reply(pick("Проверка пройдена. Играй честно!", "Чисто! Приятной игры на HolyWorld."))));

        rules.add(new Rule("can_play", 34, (m,l,s) -> has(l, "можно играть","можно идти","я свободен","мы закончили"),
            (m,l,s) -> Result.reply("Да, свободен! Приятной игры.")));

        rules.add(new Rule("trying", 32, (m,l,s) -> has(l, "попробую","постараюсь","запускаю","открываю","включаю","пытаюсь"),
            (m,l,s) -> { s.awaitingAnydeskResponse = false; return Result.reply(pick("Жду.", "Давай!", "Ок, " + s.remaining() + " мин.")); }));

        rules.add(new Rule("instruction", 30, (m,l,s) -> has(l, "инструкция","как пройти проверку","объясни"),
            (m,l,s) -> Result.reply("1. anydesk com 2. Скачай 3. Открой 4. Скинь ID мне в ЛС 5. Прими запрос")));

        rules.add(new Rule("linux_mac", 28, (m,l,s) -> has(l, "линукс","linux","макос","macos"),
            (m,l,s) -> Result.reply("AnyDesk работает на любой ОС. Качай с anydesk com.")));

        // ==================== 10: ПРИВЕТСТВИЕ ====================

        rules.add(new Rule("greeting", 10,
            (m,l,s) -> {
                if (s.sentGreeting) return false;
                return s.msgCount <= 2 || has(l, "привет","прив","хай","здравств","салам","здаров","приветствую","хелло","hello") || eq(l, "ку","qq","hi","yo");
            },
            (m,l,s) -> {
                s.sentGreeting = true;
                s.askedForAnydesk = true;
                s.awaitingAnydeskResponse = true;
                return Result.reply(pick(
                    "Привет! Это проверка на читы. У тебя 7 минут. Скачивай AnyDesk (anydesk com), запускай и кидай мне код в ЛС. Признание = 20 дней бана. Отказ или выход = 30 дней.",
                    "Добрый день. Проверка на наличие стороннего ПО. 7 минут на скачивание AnyDesk (anydesk com). Признание = 20 дней, отказ = 30 дней.",
                    "Привет. Проверка на читы. Качай AnyDesk с anydesk com и скинь мне ID. У тебя 7 минут."
                ));
            }));

        // ==================== 5: АВТО-ПРЕДЛОЖЕНИЯ ====================

        rules.add(new Rule("auto_confession_offer", 5,
            (m,l,s) -> s.msgCount > 6 && !s.offeredConfession && s.elapsed() >= 3,
            (m,l,s) -> {
                s.offeredConfession = true;
                return Result.reply("Напоминаю: признание = 20 дней, отказ = 30 дней. Напиши \"Я чит\" для признания или \"Отказ\" для отказа.");
            }));

        rules.add(new Rule("time_warning", 3,
            (m,l,s) -> !s.warnedTime && s.elapsed() >= 5,
            (m,l,s) -> {
                s.warnedTime = true;
                return Result.reply("Внимание! Осталось " + s.remaining() + " мин! Качай AnyDesk сейчас или напиши \"Отказ\"!");
            }));

        // ==================== 0: ДЕФОЛТ ====================

        rules.add(new Rule("default", 0,
            (m,l,s) -> true,
            (m,l,s) -> {
                if (s.msgCount <= 1) {
                    s.sentGreeting = true;
                    s.askedForAnydesk = true;
                    s.awaitingAnydeskResponse = true;
                    return Result.reply("Привет! Это проверка на читы. У тебя 7 минут. Качай AnyDesk (anydesk com) и кидай код в ЛС.");
                }
                return Result.reply(pick(
                    "Жду AnyDesk. Время идёт.",
                    "Скачивай AnyDesk - anydesk com. Осталось " + s.remaining() + " мин.",
                    "Не трать время. Качай AnyDesk.",
                    "Жду код из AnyDesk. " + s.remaining() + " мин."
                ));
            }));
    }

    // ======================== API ========================

    public Result getResult(String msg, String name) {
        if (msg == null || msg.trim().isEmpty()) return null;
        String low = msg.toLowerCase().trim();
        PlayerState st = states.computeIfAbsent(name, k -> new PlayerState());
        st.msgCount++;

        for (Rule r : rules) {
            try {
                if (r.match.test(msg, low, st)) {
                    Result res = r.reply.get(msg, low, st);
                    st.lastCat = r.cat;
                    HolyWorldAutoReply.LOGGER.info("[AI] [{}] '{}' -> {}", r.cat, msg, res.action);
                    return res;
                }
            } catch (Exception e) {
                HolyWorldAutoReply.LOGGER.error("[AI] Ошибка {}: {}", r.cat, e.getMessage());
            }
        }
        return null;
    }

    public void clearPlayerState(String n) { states.remove(n); }
    public void clearAllStates() { states.clear(); }
}
