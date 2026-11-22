package com.visionmod;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;

/**
 * Original Vision dashboard 2.0. Unlike the legacy screen this uses a searchable
 * two-column card grid with category navigation, details and real module actions.
 *
 * No external Fading files, UI resources or bytecode are used.
 * The dashboard deliberately reuses existing module toggles/settings so that
 * a visual refresh does not silently reset the user's configurations.
 */
public final class VisionDashboardScreen extends Screen {
    private record Module(int category, String title, String info,
                          BooleanSupplier enabled, Runnable toggle, Runnable options) { }

    private static final int VISUAL = 0;
    private static final int HUD = 1;
    private static final int COMBAT = 2;
    private static final int WORLD = 3;
    private static final int MOTION = 4;
    private static final int TOOLS = 5;
    private static final int PERFORMANCE = 6;
    private static final String[] TABS = {
        "Визуал", "Интерфейс", "Бой", "Мир", "Движение", "Утилиты", "Производительность"
    };
    private static final String[] CAPTIONS={
        "Свет, окружение и видимость", "Информация во время игры", "Отклик и эффекты сражений",
        "Детали, которые оживляют мир", "Плавность, камера и анимации", "Повседневные удобства", "Баланс качества и скорости"
    };
    private static final int WHITE=VisionGuiTheme.WHITE, MUTED=VisionGuiTheme.MUTED;
    private static final List<Module> CATALOG = catalog();

    private int category;
    private int page;
    private Module details;
    private int modal = 0; // 0: cards; 1: ore settings; 2: advanced tools
    private static final String[] ORE_NAMES = {
        "DIAMOND", "DEBRIS", "IRON", "GOLD", "REDSTONE",
        "EMERALD", "LAPIS", "COPPER", "COAL", "QUARTZ"
    };
    private static final String[] ADVANCED_TOOLS = {
        "ORE COLORS", "PARTICLES", "CROSSHAIR", "PERFORMANCE", "CONFIGS"
    };
    private int captureBind = -1;
    private EditBox search;
    private EditBox customText;
    private List<Module> cachedModules = List.of();
    private String cachedQuery = null;
    private int cachedCategory = -1;
    private DashboardLayout layout;
    private final java.util.Map<Module,Float> switchPositions=new java.util.IdentityHashMap<>();
    private long renderedAt;
    private final java.util.Map<Module,Float> hoverPositions=new java.util.IdentityHashMap<>();
    private float navigationY=Float.NaN;
    private String hoverLabel="";
    private long hoverStarted;

    private int left, top, w, h, sidebar, contentX, contentW, columns, rows, cardsPerPage;

    public VisionDashboardScreen() {
        super(Component.literal("VISION 2.0"));
    }

    private static List<Module> catalog() {
        List<Module> m = new ArrayList<>();
        visual(m,"FullBright","Мягкий свет в тёмных местах",0);
        visual(m,"Ore Highlight","Выбор руд и собственных цветов",1);
        visual(m,"Clear Water","Прозрачность и видимость под водой",2);
        visual(m,"Clear Lava","Чёткий обзор в лаве",3);
        visual(m,"Player Hearts","Здоровье рядом с ником игрока",4);
        visual(m,"Particles","Частицы блоков, порталов и движения",5);
        m.add(new Module(VISUAL,"Free Look","Зажми Alt, чтобы осмотреться",
            ()->FreeLook.enabled,()->FreeLook.setEnabled(!FreeLook.enabled),
            ()->{FreeLook.smooth=!FreeLook.smooth;VisionExtras.save();}));
        m.add(new Module(VISUAL,"Скрыть ники и бирки","Подписи над игроками и мобами в мире",
            ()->VisionAppearance.hideEntityNames,VisionAppearance::toggleEntityNames,null));
        extras(m,HUD,"CPS Counter","Текущая скорость кликов",0);
        extras(m,HUD,"Armor HUD","Состояние надетой брони",1);
        extras(m,HUD,"Food + Sat","Голод и насыщение на одном виджете",2);
        extras(m,HUD,"Crosshair Editor","Форма, размер и цвет прицела",3);
        extras(m,HUD,"Shulker Summary","Содержимое шалкеров в одном списке",9);
        extras(m,HUD,"Snowflakes","Лёгкие снежинки вокруг игрока",7);
        extras(m,HUD,"Air Hearts","Плавные воздушные сердечки",8);
        m.add(new Module(HUD,"Tab Heads","Головы игроков в списке Tab",
            ()->VisionTabHeads.enabled,VisionTabHeads::toggle,null));
        extras(m,HUD,"Shulker Tooltip","Предпросмотр содержимого шалкера",15);
        feature(m,HUD,0); feature(m,HUD,1); feature(m,HUD,2);
        feature(m,HUD,4); feature(m,HUD,5);
        feature(m,COMBAT,3); feature(m,COMBAT,6); feature(m,COMBAT,7);
        world(m,COMBAT,0);
        tools(m,COMBAT,2);
        extended(m,COMBAT,1); extended(m,COMBAT,6);
        extras(m,WORLD,"Block Outline","Акцент на выбранном блоке",6);
        extras(m,WORLD,"Low Fire","Огонь, который не закрывает обзор",14);
        world(m,WORLD,1); world(m,WORLD,2); world(m,WORLD,3);
        extended(m,WORLD,0); extended(m,WORLD,2);
        extended(m,WORLD,3); extended(m,WORLD,4);
        extended(m,WORLD,7);
        extras(m,MOTION,"Zoom","Плавное приближение по клавише",4);
        motion(m,MOTION,0); motion(m,MOTION,1); motion(m,MOTION,2);
        motion(m,MOTION,3); motion(m,MOTION,4);
        extras(m,TOOLS,"Fast Scroller","Shift + колесо или Shift + ЛКМ по слотам",5);
        tools(m,TOOLS,0); tools(m,TOOLS,1);
        extended(m,TOOLS,5);
        extras(m,TOOLS,"Arrow Hit Sound","Звук попадания стрелой",10);
        extras(m,TOOLS,"Small Sword","Компактный меч в руке",11);
        extras(m,TOOLS,"Small Shield","Компактный щит в руке",12);
        extras(m,TOOLS,"Small Totem","Компактный тотем в руке",13);
        m.add(new Module(TOOLS,"Totem Animation","Компактная анимация активации тотема",()->VisionAdvanced.smallTotemAnimation,
            ()->{VisionAdvanced.smallTotemAnimation=!VisionAdvanced.smallTotemAnimation;VisionAdvanced.save();},VisionAdvanced::cycleTotemSize));
        m.add(new Module(TOOLS,"Shulker Grid","Предпросмотр 9 × 3 в инвентаре",()->VisionAdvanced.shulkerGrid,
            ()->{VisionAdvanced.shulkerGrid=!VisionAdvanced.shulkerGrid;VisionAdvanced.save();},null));
        m.add(new Module(PERFORMANCE,"Real FPS Boost",
            "Удобный баланс качества и скорости",
            ()->VisionRenderBoost.enabled,VisionRenderBoost::toggle,null));
        m.add(new Module(PERFORMANCE,"Adaptive Budgets",
            "Меньше нагрузки в сложных сценах",
            ()->VisionPerformance.adaptive,()->VisionHotkeys.togglePerformance(0),null));
        m.add(new Module(PERFORMANCE,"HUD Throttle",
            "Лёгкий интерфейс в людных местах",
            ()->VisionPerformance.limitHudOnBusySpawn,
            ()->VisionHotkeys.togglePerformance(1),null));
        m.add(new Module(PERFORMANCE,"Ambient Throttle",
            "Меньше фоновых эффектов при просадках",
            ()->VisionPerformance.reduceAmbientOnBusySpawn,
            ()->VisionHotkeys.togglePerformance(2),null));
        return List.copyOf(m);
    }

    private static void visual(List<Module> m,String name,String info,int i) {
        m.add(new Module(VISUAL,name,info,
            ()->switch(i) {
                case 0 -> VisionModClient.fullBright;
                case 1 -> VisionModClient.oreHighlight;
                case 2 -> VisionModClient.clearWater;
                case 3 -> VisionModClient.clearLava;
                case 4 -> VisionModClient.playerHearts;
                case 5 -> VisionModClient.particles;
                default -> false;
            },
            ()->{
                switch(i) {
                    case 0 -> VisionModClient.fullBright=!VisionModClient.fullBright;
                    case 1 -> VisionModClient.oreHighlight=!VisionModClient.oreHighlight;
                    case 2 -> VisionModClient.clearWater=!VisionModClient.clearWater;
                    case 3 -> VisionModClient.clearLava=!VisionModClient.clearLava;
                    case 4 -> VisionModClient.playerHearts=!VisionModClient.playerHearts;
                    case 5 -> VisionModClient.particles=!VisionModClient.particles;
                    default -> {}
                }
                VisionPerformance.custom();
            },
            i==0 ? VisionAppearance::cycleBrightness : i==1 ? VisionDashboardScreen::openOres
                : i==5 ? ()->net.minecraft.client.Minecraft.getInstance().setScreen(new ParticleSettingsScreen())
                : null));
    }

    private static void extras(List<Module> m,int category,String title,String info,int id) {
        Runnable options = switch(id) {
            case 3 -> ()->net.minecraft.client.Minecraft.getInstance().setScreen(new VisionCrosshairEditorScreen());
            case 4 -> VisionExtras::cycleZoom;
            case 6 -> ()->net.minecraft.client.Minecraft.getInstance().setScreen(new OreColorPickerScreen("Контур блока",()->VisionExtras.outlineColor,color->{VisionExtras.outlineColor=color;VisionExtras.save();},null));
            case 7,8 -> VisionExtras::cycleAmbientStyle;
            default -> null;
        };
        m.add(new Module(category,title,info,()->VisionExtras.enabled(id),
            ()->VisionExtras.toggle(id),options));
    }

    private static void feature(List<Module> m,int category,int id) {
        m.add(new Module(category,VisionFeaturePack.NAMES[id],
            switch(id) {
                case 0 -> "Кинематографичные полосы экрана";
                case 1 -> "Цветной индикатор прочности брони";
                case 2 -> "Здоровье выбранной цели";
                case 3 -> "Расстояние до выбранной цели";
                case 4 -> "Обратный отсчёт ближайшего TNT";
                case 5 -> "Метка рядом с летящей стрелой";
                case 6 -> "Эффект успешного попадания";
                default -> "Мягкая виньетка при попадании";
            },
            ()->VisionFeaturePack.enabled(id),()->VisionFeaturePack.toggle(id),null));
    }

    private static void world(List<Module> m,int category,int id) {
        m.add(new Module(category,VisionWorldEffects.NAMES[id],
            switch(id) {
                case 0 -> "Контур выбранного противника";
                case 1 -> "Плавный след с цветовым переходом";
                case 2 -> "Расходящиеся кольца при приземлении";
                default -> "Декоративная шляпа для вида от третьего лица";
            },
            ()->VisionWorldEffects.enabled(id),()->VisionWorldEffects.toggle(id),
            id==1 ? ()->net.minecraft.client.Minecraft.getInstance().setScreen(new VisionTrailSettingsScreen()) : null));
    }

    private static void extended(List<Module> m,int category,int id) {
        m.add(new Module(category,VisionExtendedFeatures.NAMES[id],
            switch(id) {
                case 0 -> "Подсветка выбранных блоков";
                case 1 -> "Уведомление о поражении цели";
                case 2 -> "Тёплые и холодные оттенки мира";
                case 3 -> "Музыка из системного плеера · без расширений";
                case 4 -> "Плавное следование за камерой и прыжками";
                case 5 -> "Панель друзей поблизости · до 48 блоков";
                case 6 -> "Звуковой отклик на попадание";
                default -> "Настройка погодных частиц";
            },
            ()->VisionExtendedFeatures.enabled(id),
            ()->VisionExtendedFeatures.toggle(id),
            id==6 ? VisionExtendedFeatures::cycleSound :
            id==3 ? ()->net.minecraft.client.Minecraft.getInstance().setScreen(new VisionLyricsScreen()) :
            id==2 ? VisionExtendedFeatures::cycleWorldColor :
                id==4 ? VisionExtendedFeatures::cyclePetColor : null));
    }

    private static void motion(List<Module> m,int category,int id) {
        m.add(new Module(category,VisionMotionEffects.NAMES[id],
            switch(id) {
                case 0 -> "Новая плавная анимация взмаха";
                case 1 -> "Предметы ложатся на землю и замирают";
                case 2 -> "Световой след при реальном рывке копьём";
                case 3 -> "Импульс в точке попадания копьём · включая разгон";
                default -> "Лёгкие штрихи при движении камеры";
            },
            ()->VisionMotionEffects.selected(id),
            ()->VisionMotionEffects.toggle(id),id==2||id==3?
                ()->net.minecraft.client.Minecraft.getInstance().setScreen(new VisionSpearSettingsScreen()):null));
    }

    private static void tools(List<Module> m,int category,int id) {
        m.add(new Module(category,VisionTools2.NAMES[id],
            switch(id) {
                case 0 -> "Повторное использование предметов";
                case 1 -> "JSON-модель на теле · пример: рюкзак";
                default -> "Цвет попаданий и контура цели";
            },
            ()->VisionTools2.selected(id),()->VisionTools2.toggle(id),
            id==2 ? VisionTools2::cycleHitColor : null));
    }

    private static void openOres() {
        var screen = net.minecraft.client.Minecraft.getInstance().screen;
        if (screen instanceof VisionDashboardScreen dashboard) dashboard.modal = 1;
    }

    /** Keep all original hotkey IDs, never renumber older player configs. */
    private static int bindingId(Module m) {
        return switch(m.title) {
            case "FullBright" -> VisionHotkeys.visual(0);
            case "Ore Highlight" -> VisionHotkeys.visual(1);
            case "Clear Water" -> VisionHotkeys.visual(2);
            case "Clear Lava" -> VisionHotkeys.visual(3);
            case "Player Hearts" -> VisionHotkeys.visual(4);
            case "Particles" -> VisionHotkeys.visual(5);
            case "Free Look" -> VisionHotkeys.visual(6);
            case "CPS Counter" -> VisionHotkeys.extra(0);
            case "Armor HUD" -> VisionHotkeys.extra(1);
            case "Food + Sat" -> VisionHotkeys.extra(2);
            case "Crosshair Editor" -> VisionHotkeys.extra(3);
            case "Zoom" -> VisionHotkeys.extra(4);
            case "Fast Scroller" -> VisionHotkeys.extra(5);
            case "Block Outline" -> VisionHotkeys.extra(6);
            case "Snowflakes" -> VisionHotkeys.extra(7);
            case "Air Hearts" -> VisionHotkeys.extra(8);
            case "Shulker Summary" -> VisionHotkeys.extra(9);
            case "Arrow Hit Sound" -> VisionHotkeys.extra(10);
            case "Small Sword" -> VisionHotkeys.extra(11);
            case "Small Shield" -> VisionHotkeys.extra(12);
            case "Small Totem" -> VisionHotkeys.extra(13);
            case "Low Fire" -> VisionHotkeys.extra(14);
            case "Shulker Tooltip" -> VisionHotkeys.extra(15);
            case "Totem Animation" -> VisionHotkeys.totemAnimationToggle();
            case "Shulker Grid" -> VisionHotkeys.shulkerGridToggle();
            case "Real FPS Boost" -> VisionHotkeys.boostToggle();
            case "Adaptive Budgets" -> VisionHotkeys.performanceToggle(0);
            case "HUD Throttle" -> VisionHotkeys.performanceToggle(1);
            case "Ambient Throttle" -> VisionHotkeys.performanceToggle(2);
            default -> -1;
        };
    }


    @Override protected void init() {
        String previousQuery=search==null?"":search.getValue();
        layout=DashboardLayout.of(width,height);
        w=layout.width();h=layout.height();sidebar=layout.sidebar();
        left=(width-w)/2;top=(height-h)/2;
        contentX=left+layout.contentLeft();contentW=layout.contentWidth();
        navigationY=Float.NaN;
        columns=layout.columns();rows=layout.rows();cardsPerPage=layout.cards();
        search=new EditBox(font,contentX+29,top+layout.searchTop()+8,contentW-56,16,Component.literal("Поиск"));
        search.setBordered(false);
        search.setMaxLength(48);search.setHint(Component.empty());
        search.setTextColor(WHITE);
        search.setValue(previousQuery);
        search.setResponder(value->{page=0;cachedQuery=null;});
        addRenderableWidget(search);
        customText=new EditBox(font,left+24,top+116,w-48,20,Component.literal("Друзья через запятую"));
        customText.setMaxLength(120);customText.visible=false;customText.active=false;
        addRenderableWidget(customText);
        if(details!=null)openDetails(details);
        page=Math.min(page,maxPage(filtered()));
        VisionMenuSession.entered(this);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta) {}
    private int accent(){return VisionMenuTheme.CYAN;}
    private static boolean inside(double mx,double my,int x,int y,int w,int h){return mx>=x&&my>=y&&mx<x+w&&my<y+h;}
    private static void round(GuiGraphics g,int x,int y,int w,int h,int r,int color){VisionGuiTheme.round(g,x,y,w,h,r,color);}
    private void textFit(GuiGraphics g,String text,int x,int y,int max,int color){
        VisionFonts.fit(g,font,text,x,y,max,color);
    }
    private void button(GuiGraphics g,int x,int y,int w,int h,String text,boolean selected,int mx,int my){
        VisionGuiTheme.button(g,x,y,w,h,selected,inside(mx,my,x,y,w,h));
        textFit(g,text,x+9,y+(h-8)/2,w-18,selected?WHITE:MUTED);
    }
    private List<Module> filtered(){
        String query=search.getValue().trim().toLowerCase(Locale.ROOT);
        if(cachedCategory==category&&query.equals(cachedQuery))return cachedModules;
        cachedModules=CATALOG.stream().filter(m->query.isEmpty()?m.category==category:
            (m.title+" "+m.info).toLowerCase(Locale.ROOT).contains(query)).toList();
        cachedCategory=category;cachedQuery=query;return cachedModules;
    }
    private int cardX(int i){return left+layout.cardLeft(i);}
    private int cardY(int i){return top+layout.cardTop(i);}
    private int cardWidth(){return layout.cardWidth();}
    private int maxPage(List<Module> m){return Math.max(0,(m.size()-1)/cardsPerPage);}

    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        long now=System.nanoTime();
        float approach=!VisionAppearance.animations||renderedAt==0?1:
            (float)(1-Math.exp(-Math.min(.1,(now-renderedAt)/1_000_000_000.0)*18));
        renderedAt=now;
        VisionGuiTheme.backdrop(g,width,height);VisionGuiTheme.frame(g,left,top,w,h);
        round(g,left+1,top+1,sidebar-1,h-2,9,VisionGuiTheme.RAIL);
        g.fill(left+sidebar-9,top+1,left+sidebar,top+h-1,VisionGuiTheme.RAIL);
        g.fill(left+sidebar,top+12,left+sidebar+1,top+h-12,VisionGuiTheme.BORDER);
        String tooltip=renderNavigation(g,mx,my,approach);
        int sy=top+layout.searchTop(),sh=layout.searchHeight();
        round(g,contentX-1,sy-1,contentW+2,sh+2,6,search.isFocused()?VisionGuiTheme.tint(VisionGuiTheme.BORDER,.55f):VisionGuiTheme.BORDER);
        round(g,contentX,sy,contentW,sh,5,VisionGuiTheme.CARD);
        VisionGuiTheme.icon(g,VisionGuiTheme.SEARCH,contentX+10,sy+(sh-11)/2,11,search.isFocused()?accent():VisionGuiTheme.FAINT);
        if(search.getValue().isEmpty()&&!search.isFocused())
            VisionFonts.fit(g,font,"Поиск модулей…",search.getX(),search.getY(),search.getWidth(),MUTED);
        if(!search.getValue().isBlank())VisionGuiTheme.icon(g,VisionGuiTheme.CLOSE,contentX+contentW-20,sy+(sh-10)/2,10,MUTED);
        List<Module> modules=filtered();page=Math.min(page,maxPage(modules));
        String title=search.getValue().isBlank()?TABS[category]:"Результаты поиска";
        if(layout.dense())VisionFonts.heading(g,font,title,contentX,top+layout.titleTop(),contentW-65,WHITE);
        else{
            g.pose().pushMatrix();g.pose().translate(contentX,top+layout.titleTop());g.pose().scale(1.25f,1.25f);
            VisionFonts.heading(g,font,title,0,0,(int)((contentW-80)/1.25f),WHITE);g.pose().popMatrix();
            textFit(g,search.getValue().isBlank()?CAPTIONS[category]:"Найденные модули во всех разделах",contentX,top+81,contentW-10,MUTED);
        }
        long active=modules.stream().filter(m->m.enabled.getAsBoolean()).count();
        String count=active+" / "+modules.size();int countWidth=VisionFonts.width(font,count)+14;
        round(g,contentX+contentW-countWidth,top+layout.titleTop()-2,countWidth,14,4,VisionGuiTheme.CARD);
        VisionFonts.draw(g,font,count,contentX+contentW-countWidth+7,top+layout.titleTop()+1,MUTED);
        for(int i=0;i<cardsPerPage&&page*cardsPerPage+i<modules.size();i++){
            Module m=modules.get(page*cardsPerPage+i);String cardTooltip=renderCard(g,m,i,mx,my,approach);
            if(cardTooltip!=null)tooltip=cardTooltip;
        }
        if(modules.isEmpty()){
            int center=contentX+contentW/2;
            VisionGuiTheme.icon(g,VisionGuiTheme.SEARCH,center-12,top+layout.gridTop()+12,24,VisionGuiTheme.FAINT);
            String empty="Ничего не найдено";
            VisionFonts.draw(g,font,empty,center-VisionFonts.width(font,empty)/2,top+layout.gridTop()+51,WHITE);
            textFit(g,"Попробуй другое название",contentX+12,top+layout.gridTop()+68,contentW-24,MUTED);
        }
        String footerTooltip=renderFooter(g,mx,my,modules);if(footerTooltip!=null)tooltip=footerTooltip;
        search.visible=details==null&&modal==0&&captureBind<0;search.active=search.visible;
        if(details!=null)renderDetails(g,mx,my);if(modal!=0)renderModal(g,mx,my);
        if(captureBind>=0){
            overlay(g,"Горячая клавиша");
            textFit(g,"Нажми клавишу или кнопку мыши.",left+24,top+64,w-48,WHITE);
            textFit(g,"Esc — убрать привязку",left+24,top+85,w-48,MUTED);
        }
        VisionGuiTheme.branding(g,font,left,top,w,h);
        super.render(g,mx,my,delta);
        if(tooltip==null||details!=null||modal!=0||captureBind>=0){hoverLabel="";hoverStarted=now;}
        else{
            if(!tooltip.equals(hoverLabel)){hoverLabel=tooltip;hoverStarted=now;}
            if(now-hoverStarted>350_000_000L)VisionGuiTheme.tooltip(g,font,tooltip,mx,my,width,height);
        }
    }
    private String renderNavigation(GuiGraphics g,int mx,int my,float approach){
        boolean rail=layout.iconRail();int logoX=left+(rail?(sidebar-24)/2:14),logoY=top+(layout.dense()?12:18);
        round(g,logoX,logoY,24,24,7,VisionGuiTheme.tint(VisionGuiTheme.CARD,.16f));
        VisionGuiTheme.icon(g,VisionGuiTheme.LOGO,logoX+6,logoY+6,12,accent());
        if(rail)VisionFonts.draw(g,font,"2.0",left+(sidebar-VisionFonts.width(font,"2.0"))/2,logoY+31,VisionGuiTheme.FAINT);
        else{
            VisionFonts.heading(g,font,"Vision",logoX+33,logoY,sidebar-51,WHITE);
            textFit(g,"Твой игровой стиль",logoX+33,logoY+15,sidebar-51,VisionGuiTheme.FAINT);
            if(!layout.dense())textFit(g,"МОДУЛИ",left+14,top+65,sidebar-28,VisionGuiTheme.FAINT);
        }
        int target=top+layout.tabTop(category);if(!Float.isFinite(navigationY))navigationY=target;
        navigationY+=(target-navigationY)*approach;
        if(search.getValue().isBlank()){
            round(g,left+9,Math.round(navigationY),sidebar-18,layout.tabHeight(),5,VisionGuiTheme.tint(VisionGuiTheme.CARD,.13f));
            round(g,left+3,Math.round(navigationY)+3,2,layout.tabHeight()-6,1,accent());
        }
        String tooltip=null;
        for(int i=0;i<TABS.length;i++){
            int y=top+layout.tabTop(i);boolean selected=category==i&&search.getValue().isBlank(),hover=inside(mx,my,left+9,y,sidebar-18,layout.tabHeight());
            if(hover&&!selected)round(g,left+9,y,sidebar-18,layout.tabHeight(),5,VisionGuiTheme.HOVER);
            int size=layout.dense()?10:12,ix=rail?left+(sidebar-size)/2:left+17;
            VisionGuiTheme.icon(g,i,ix,y+(layout.tabHeight()-size)/2,size,selected?accent():hover?WHITE:MUTED);
            String label=i==PERFORMANCE?"Оптимизация":TABS[i];
            if(!rail)textFit(g,label,left+36,y+(layout.tabHeight()-8)/2,sidebar-48,selected?WHITE:MUTED);
            if(hover&&(rail||i==PERFORMANCE))tooltip=TABS[i];
        }
        int myy=top+layout.masterTop(),mh=layout.masterHeight();boolean over=inside(mx,my,left+9,myy,sidebar-18,mh);
        round(g,left+9,myy,sidebar-18,mh,5,over?VisionGuiTheme.HOVER:VisionGuiTheme.CARD);
        VisionGuiTheme.icon(g,VisionGuiTheme.POWER,rail?left+(sidebar-11)/2:left+17,myy+(mh-11)/2,11,VisionModClient.enabled?0xFF84DEC1:VisionGuiTheme.FAINT);
        if(!rail)textFit(g,VisionModClient.enabled?"Мод включён":"Мод выключен",left+36,myy+(mh-8)/2,sidebar-48,VisionModClient.enabled?WHITE:MUTED);
        if(over)tooltip=VisionModClient.enabled?"Выключить Vision":"Включить Vision";
        return tooltip;
    }
    private int detailsSize(){return layout.dense()?14:18;}
    private int detailsX(int i){return cardX(i)+cardWidth()-detailsSize()-8;}
    private int detailsY(int i){return cardY(i)+layout.cardHeight()-detailsSize()-6;}
    private String renderCard(GuiGraphics g,Module m,int i,int mx,int my,float approach){
        int x=cardX(i),y=cardY(i),cw=cardWidth(),ch=layout.cardHeight();boolean on=m.enabled.getAsBoolean(),hover=inside(mx,my,x,y,cw,ch);
        float target=on?1:0,position=switchPositions.getOrDefault(m,target);position+=(target-position)*approach;switchPositions.put(m,position);
        float light=hoverPositions.getOrDefault(m,0f);light+=((hover?1:0)-light)*approach;hoverPositions.put(m,light);
        int border=VisionGeometry.blendRgb(on?VisionGuiTheme.tint(VisionGuiTheme.BORDER,.18f):VisionGuiTheme.BORDER,VisionGuiTheme.tint(VisionGuiTheme.BORDER,.36f),light);
        round(g,x-1,y-1,cw+2,ch+2,7,border);round(g,x,y,cw,ch,6,VisionGeometry.blendRgb(VisionGuiTheme.CARD,VisionGuiTheme.HOVER,light));
        int tx=x+11,ty=y+10;
        if(!layout.dense()){
            round(g,x+11,y+11,25,25,6,VisionGuiTheme.tint(VisionGuiTheme.BACK,on?.14f:.025f));
            VisionGuiTheme.icon(g,m.category,x+18,y+18,11,on?accent():MUTED);tx=x+45;ty=y+16;
        }
        VisionFonts.heading(g,font,m.title,tx,ty,cw-(tx-x)-40,WHITE);
        int sx=x+cw-33,sy=y+(layout.dense()?10:16);
        round(g,sx,sy,23,12,6,VisionGeometry.blendRgb(0xFF364052,VisionGuiTheme.tint(VisionGuiTheme.CARD,.65f),position));
        round(g,sx+3+Math.round(10*position),sy+3,6,6,3,VisionGeometry.blendRgb(0xFF8491A5,0xFFF2EEFF,position));
        if(layout.dense())textFit(g,m.info,x+11,y+31,cw-38,MUTED);
        else{
            var description=font.split(VisionFonts.menu(m.info),cw-24);
            for(int row=0;row<Math.min(description.size(),2);row++)g.drawString(font,description.get(row),x+12,y+45+row*11,MUTED,false);
            round(g,x+12,y+ch-17,3,3,1,on?0xFF84DEC1:VisionGuiTheme.FAINT);
            textFit(g,on?"Активен":"Выключен",x+20,y+ch-19,80,on?MUTED:VisionGuiTheme.FAINT);
            int bind=bindingId(m);String label=bind<0?"":VisionHotkeys.label(bind);
            if(cw>230&&bind>=0){
                if(label.contains("unbound")||label.contains("MMB"))label="СКМ";
                VisionGuiTheme.icon(g,VisionGuiTheme.KEY,x+98,y+ch-19,10,VisionGuiTheme.FAINT);
                textFit(g,label.replace("Hold: ",""),x+112,y+ch-19,cw-151,VisionGuiTheme.FAINT);
            }
        }
        int dx=detailsX(i),dy=detailsY(i),ds=detailsSize();boolean gear=inside(mx,my,dx,dy,ds,ds);
        if(gear)round(g,dx,dy,ds,ds,4,VisionGuiTheme.tint(VisionGuiTheme.CARD,.2f));
        VisionGuiTheme.icon(g,VisionGuiTheme.SETTINGS,dx+(ds-9)/2,dy+(ds-9)/2,9,gear?accent():VisionGuiTheme.FAINT);
        return gear?"Параметры · ПКМ по карточке":hover&&my>y+ch-24&&!layout.dense()&&bindingId(m)>=0?
            "СКМ — назначить клавишу":hover&&my>y+(layout.dense()?25:39)?m.info:null;
    }
    private String renderFooter(GuiGraphics g,int mx,int my,List<Module> modules){
        int y=top+layout.navigationTop(),fh=layout.footerHeight();
        for(int n=0;n<2;n++){
            int x=contentX+n*32;boolean available=n==0?page>0:page<maxPage(modules),hover=inside(mx,my,x,y,26,fh);
            VisionGuiTheme.button(g,x,y,26,fh,false,available&&hover);
            VisionGuiTheme.chevron(g,n==1,x+7,y+(fh-12)/2,available?MUTED:VisionGuiTheme.FAINT);
        }
        VisionFonts.draw(g,font,(page+1)+" / "+(maxPage(modules)+1),contentX+73,y+(fh-8)/2,VisionGuiTheme.FAINT);
        int sx=left+layout.settingsLeft(),tx=left+layout.themeLeft();
        boolean settings=inside(mx,my,sx,y,layout.settingsWidth(),fh),theme=inside(mx,my,tx,y,layout.themeWidth(),fh);
        VisionGuiTheme.button(g,sx,y,layout.settingsWidth(),fh,false,settings);VisionGuiTheme.button(g,tx,y,layout.themeWidth(),fh,false,theme);
        VisionGuiTheme.icon(g,VisionGuiTheme.SETTINGS,sx+8,y+(fh-10)/2,10,MUTED);
        VisionGuiTheme.icon(g,VisionGuiTheme.PALETTE,tx+8,y+(fh-10)/2,10,accent());
        if(!layout.compactFooter()){
            textFit(g,"Параметры",sx+25,y+(fh-8)/2,layout.settingsWidth()-31,MUTED);
            textFit(g,"Тема",tx+25,y+(fh-8)/2,layout.themeWidth()-31,MUTED);
        }
        return settings?"Настройки модулей":theme?"Внешний вид и цветовая тема":null;
    }
    private void overlay(GuiGraphics g,String title){
        round(g,left+8,top+8,w-16,h-16,8,VisionGuiTheme.BACK);
        VisionFonts.heading(g,font,title,left+24,top+25,w-66,WHITE);
        VisionFonts.draw(g,font,"×",left+w-30,top+24,MUTED);
        g.fill(left+24,top+44,left+w-24,top+45,VisionGuiTheme.BORDER);
    }
    private void renderModal(GuiGraphics g,int mx,int my){
        overlay(g,modal==1?"РУДЫ · ПКМ: ЦВЕТ":"НАСТРОЙКИ");
        int gap=10,cw=(w-58)/2;
        if(modal==1){
            for(int i=0;i<ORE_NAMES.length;i++){
                int x=left+24+(i%2)*(cw+gap),y=top+56+(i/2)*28;
                button(g,x,y,cw,24,ORE_NAMES[i],OreSelection.enabled(i),mx,my);
                g.fill(x+cw-16,y+8,x+cw-8,y+16,OreColors.getColor(i));
            }
        }else{
            for(int i=0;i<ADVANCED_TOOLS.length;i++)button(g,left+24+(i%2)*(cw+gap),top+56+(i/2)*34,cw,28,ADVANCED_TOOLS[i],false,mx,my);
            if(h>238){
                button(g,left+24,top+168,w-48,24,"Вернуть позиции HUD",false,mx,my);
                textFit(g,"Открой чат: ЛКМ — двигать, ПКМ — сбросить.",left+24,top+201,w-48,MUTED);
            }
        }
    }
    private void openAdvancedTool(int i){
        switch(i){case 0->modal=1;case 1->minecraft.setScreen(new ParticleSettingsScreen());
            case 2->minecraft.setScreen(new VisionCrosshairEditorScreen());case 3->minecraft.setScreen(new PerformanceScreen());
            case 4->minecraft.setScreen(new ConfigsScreen());default->{}}
    }
    private void openDetails(Module module){
        details=module;search.setFocused(false);
        customText.visible=module.title.equals("Social Radar");customText.active=customText.visible;
        customText.setResponder(v->{});
        if(customText.visible){customText.setValue(VisionExtendedFeatures.friendNames());customText.setResponder(VisionExtendedFeatures::setFriendNames);}
    }
    private void closeDetails(){details=null;customText.visible=false;customText.active=false;customText.setFocused(false);}
    private void renderDetails(GuiGraphics g,int mx,int my){
        overlay(g,details.title);
        int y=top+59;
        var rows=font.split(VisionFonts.menu(details.info),w-48);
        for(int i=0;i<Math.min(2,rows.size());i++){
            g.drawString(font,rows.get(i),left+24,y+i*13,MUTED,false);
        }
        String value=switch(details.title){
            case "Air Hearts","Snowflakes" -> "Режим: "+VisionExtras.ambientStyleLabel()+"  ·  Плотность: "+VisionExtras.ambientDensityLabel();
            case "Free Look" -> "Сглаживание: "+(FreeLook.smooth?"ВКЛ":"ВЫКЛ");
            case "Local Pet" -> VisionPetRenderer.suspended()?"Ошибка рендера · выключи и включи для повтора":"Цвет: "+VisionExtendedFeatures.petColorLabel();
            case "Trails","China Hat","Jump Circles","Target ESP" -> VisionWorldEffects.suspended()?"Ошибка рендера · выключи и включи для повтора":"Эффект виден в мире · F5: третье лицо";
            case "FullBright" -> "Яркость: "+VisionAppearance.brightness;
            case "Totem Animation" -> "Масштаб: "+VisionAdvanced.totemAnimationPercent+"%";
            case "Custom Models" -> VisionCustomModels.suspended()?"Ошибка модели · включи заново для повтора":"config/visionmod/custom-model.json";
            case "Custom Sound" -> "Звук: "+VisionExtendedFeatures.soundLabel();
            case "World Color" -> "Палитра: "+VisionExtendedFeatures.colorLabel();
            case "Hit Color" -> "Цвет: "+VisionTools2.hitColorName();
            case "World Lyrics" -> VisionMediaSession.state();
            case "Social Radar" -> "Друзья через запятую";
            default -> details.enabled.getAsBoolean()?"Модуль включён":"Модуль выключен";
        };
        textFit(g,value,left+24,top+97,w-48,WHITE);
        if(details.title.equals("Social Radar"))textFit(g,VisionExtendedFeatures.nearbyFriends(),left+24,top+142,w-48,accent());
        int bw=(w-58)/2,by=top+h-72;
        button(g,left+24,by,bw,26,details.enabled.getAsBoolean()?"Выключить":"Включить",details.enabled.getAsBoolean(),mx,my);
        if(details.options!=null)button(g,left+34+bw,by,bw,26,"Настроить",false,mx,my);
        if(details.title.equals("Air Hearts")||details.title.equals("Snowflakes"))button(g,left+24,top+h-39,w-48,22,"Изменить плотность",false,mx,my);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubled){
        double mx=e.x(),my=e.y();int b=e.button();
        if(captureBind>=0){VisionHotkeys.assign(captureBind,-b-1);captureBind=-1;return true;}
        if(details!=null||modal!=0){
            if(b==0&&inside(mx,my,left+w-43,top+12,30,28)){modal=0;closeDetails();return true;}
            if(details!=null){
                if(customText.visible&&inside(mx,my,customText.getX(),customText.getY(),customText.getWidth(),20))return super.mouseClicked(e,doubled);
                int bw=(w-58)/2;
                if(b==0){
                    if(inside(mx,my,left+24,top+h-72,bw,26))details.toggle.run();
                    else if(details.options!=null&&inside(mx,my,left+34+bw,top+h-72,bw,26))details.options.run();
                    else if((details.title.equals("Air Hearts")||details.title.equals("Snowflakes"))&&inside(mx,my,left+24,top+h-39,w-48,22))VisionExtras.cycleAmbientDensity();
                }
            }else{
                int cw=(w-58)/2;
                for(int i=0;i<(modal==1?10:ADVANCED_TOOLS.length);i++){
                    int x=left+24+(i%2)*(cw+10),y=top+56+(i/2)*(modal==1?28:34);
                    if(!inside(mx,my,x,y,cw,modal==1?24:28))continue;
                    if(modal==1){if(b==0)OreSelection.toggle(i);if(b==1)minecraft.setScreen(new OreColorPickerScreen(i));}
                    else if(b==0)openAdvancedTool(i);
                    return true;
                }
                if(modal==2&&h>238&&b==0&&inside(mx,my,left+24,top+168,w-48,24))VisionHudLayout.reset();
            }
            return true;
        }
        if(b==0&&inside(mx,my,contentX,top+layout.searchTop(),contentW,layout.searchHeight())){
            if(mx>=contentX+contentW-24&&!search.getValue().isBlank()){search.setValue("");setFocused(search);search.setFocused(true);return true;}
            if(inside(mx,my,search.getX(),search.getY(),search.getWidth(),search.getHeight()))return super.mouseClicked(e,doubled);
            setFocused(search);search.setFocused(true);return true;
        }
        if(b==0){
            for(int i=0;i<TABS.length;i++)if(inside(mx,my,left+9,top+layout.tabTop(i),sidebar-18,layout.tabHeight())){
                category=i;page=0;search.setValue("");search.setFocused(false);return true;
            }
            if(inside(mx,my,left+9,top+layout.masterTop(),sidebar-18,layout.masterHeight())){VisionModClient.enabled=!VisionModClient.enabled;return true;}
            if(inside(mx,my,left+layout.settingsLeft(),top+layout.navigationTop(),layout.settingsWidth(),layout.footerHeight())){modal=2;search.setFocused(false);return true;}
            if(inside(mx,my,left+layout.themeLeft(),top+layout.navigationTop(),layout.themeWidth(),layout.footerHeight())){minecraft.setScreen(new VisionAppearanceScreen());return true;}
        }
        List<Module> modules=filtered();
        if(b==0){
            if(page>0&&inside(mx,my,contentX,top+layout.navigationTop(),26,layout.footerHeight())){page--;return true;}
            if(page<maxPage(modules)&&inside(mx,my,contentX+32,top+layout.navigationTop(),26,layout.footerHeight())){page++;return true;}
        }
        for(int i=0;i<cardsPerPage&&page*cardsPerPage+i<modules.size();i++)if(inside(mx,my,cardX(i),cardY(i),cardWidth(),layout.cardHeight())){
            Module m=modules.get(page*cardsPerPage+i);
            search.setFocused(false);
            if(b==0){if(inside(mx,my,detailsX(i),detailsY(i),detailsSize(),detailsSize()))openDetails(m);else m.toggle.run();}
            else if(b==1)openDetails(m);
            else if(b==2&&bindingId(m)>=0)captureBind=bindingId(m);
            return true;
        }
        return super.mouseClicked(e,doubled);
    }
    @Override public boolean keyPressed(KeyEvent e){
        if(captureBind>=0){VisionHotkeys.assign(captureBind,e.key()==GLFW.GLFW_KEY_ESCAPE?0:e.key());captureBind=-1;return true;}
        if(VisionMenuSession.closeFromKey(this,e))return true;
        if(e.key()==GLFW.GLFW_KEY_ESCAPE&&(details!=null||modal!=0)){modal=0;closeDetails();return true;}
        if(details!=null||modal!=0){
            if(customText.visible)return super.keyPressed(e);
            return true;
        }
        return super.keyPressed(e);
    }
    @Override public boolean isPauseScreen(){return false;}
}
