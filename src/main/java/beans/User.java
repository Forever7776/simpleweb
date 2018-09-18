package beans;

import common.db.Entity;

import java.sql.Timestamp;

/**
 * 注册用户
 *
 * @author liudong
 */
public class User extends Entity {

    public final static User ME = new User();

    //用户权限
    public final static int ROLE_GENERAL = 1;

    private long id;//id
    private String email;                    //邮箱
    private String name;                    //呢称
    private String pwd;                    //密码
    private byte type = 0x01;                //类型
    private String ident;                    //标志

    private String portrait;                //头像
    private String lportrait;                //大头像
    private Timestamp portrait_update;    //头像更新时间
    private byte gender;                    //性别
    private String province;                //省
    private String city;                    //市
    private String region;                //地区
    private String location;                //地址
    private int role;                        //角色
    private int score;                    //分数
    private int honor_score;              //荣誉积分
    private int active_score;             //活跃积分
    private int avail_score;              //可用积分
    private byte rank;
    private int options;
    private byte status;                    //参考Pojo类的STATUS_PENDING静态变量
    private byte online;                    //是否在线
    private String notify_email;            //验证邮箱
    private byte notify_email_validated;    //邮箱是否通过验证了
    private Timestamp reg_time;            //注册时间
    private Timestamp this_login_time;    //登陆时间
    private String this_login_ip;            //登陆ip
    private Timestamp last_login_time;    //最后一次登陆时间
    private String last_login_ip;            //最后一次登陆ip
    private String activate_code;
    private Timestamp gag_time;            //禁言设置
    private int gag_forever;
    private byte pwd_version;
    private String enterprise_email;        //企业邮箱
    private byte enterprise_email_validated;
    private String tel;                    //手机号码
    private byte tel_validated;            //手机是否被验证
    private long git_id;                    //git id
    private String git_email;                //git 邮箱
    private String git_pwd;                //git 密码
    private String from;                    //用户来源,比如珠海原创会,某某活动等等

    public final static transient byte ONLINE = 0x01;
    public final static transient byte OFFLINE = 0x00;

    public final static transient byte STATUS_DISABLED = 0x04;//用户账号屏蔽状态
    public final static int ROLE_SYSTEM_MANAGER 		= 0x200;	//系统管理员

    @Override
    public long getId() {
        return id;
    }

    @Override
    public void setId(long id) {
        this.id = id;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPwd() {
        return pwd;
    }

    public void setPwd(String pwd) {
        this.pwd = pwd;
    }

    public byte getType() {
        return type;
    }

    public void setType(byte type) {
        this.type = type;
    }

    public String getIdent() {
        return ident;
    }

    public void setIdent(String ident) {
        this.ident = ident;
    }

    public String getPortrait() {
        return portrait;
    }

    public void setPortrait(String portrait) {
        this.portrait = portrait;
    }

    public String getLportrait() {
        return lportrait;
    }

    public void setLportrait(String lportrait) {
        this.lportrait = lportrait;
    }

    public Timestamp getPortrait_update() {
        return portrait_update;
    }

    public void setPortrait_update(Timestamp portrait_update) {
        this.portrait_update = portrait_update;
    }

    public byte getGender() {
        return gender;
    }

    public void setGender(byte gender) {
        this.gender = gender;
    }

    public String getProvince() {
        return province;
    }

    public void setProvince(String province) {
        this.province = province;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public int getRole() {
        return role;
    }

    public void setRole(int role) {
        this.role = role;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public int getHonor_score() {
        return honor_score;
    }

    public void setHonor_score(int honor_score) {
        this.honor_score = honor_score;
    }

    public int getActive_score() {
        return active_score;
    }

    public void setActive_score(int active_score) {
        this.active_score = active_score;
    }

    public int getAvail_score() {
        return avail_score;
    }

    public void setAvail_score(int avail_score) {
        this.avail_score = avail_score;
    }

    public byte getRank() {
        return rank;
    }

    public void setRank(byte rank) {
        this.rank = rank;
    }

    public int getOptions() {
        return options;
    }

    public void setOptions(int options) {
        this.options = options;
    }

    public byte getStatus() {
        return status;
    }

    public void setStatus(byte status) {
        this.status = status;
    }

    public byte getOnline() {
        return online;
    }

    public void setOnline(byte online) {
        this.online = online;
    }

    public String getNotify_email() {
        return notify_email;
    }

    public void setNotify_email(String notify_email) {
        this.notify_email = notify_email;
    }

    public byte getNotify_email_validated() {
        return notify_email_validated;
    }

    public void setNotify_email_validated(byte notify_email_validated) {
        this.notify_email_validated = notify_email_validated;
    }

    public Timestamp getReg_time() {
        return reg_time;
    }

    public void setReg_time(Timestamp reg_time) {
        this.reg_time = reg_time;
    }

    public Timestamp getThis_login_time() {
        return this_login_time;
    }

    public void setThis_login_time(Timestamp this_login_time) {
        this.this_login_time = this_login_time;
    }

    public String getThis_login_ip() {
        return this_login_ip;
    }

    public void setThis_login_ip(String this_login_ip) {
        this.this_login_ip = this_login_ip;
    }

    public Timestamp getLast_login_time() {
        return last_login_time;
    }

    public void setLast_login_time(Timestamp last_login_time) {
        this.last_login_time = last_login_time;
    }

    public String getLast_login_ip() {
        return last_login_ip;
    }

    public void setLast_login_ip(String last_login_ip) {
        this.last_login_ip = last_login_ip;
    }

    public String getActivate_code() {
        return activate_code;
    }

    public void setActivate_code(String activate_code) {
        this.activate_code = activate_code;
    }

    public Timestamp getGag_time() {
        return gag_time;
    }

    public void setGag_time(Timestamp gag_time) {
        this.gag_time = gag_time;
    }

    public int getGag_forever() {
        return gag_forever;
    }

    public void setGag_forever(int gag_forever) {
        this.gag_forever = gag_forever;
    }

    public byte getPwd_version() {
        return pwd_version;
    }

    public void setPwd_version(byte pwd_version) {
        this.pwd_version = pwd_version;
    }

    public String getEnterprise_email() {
        return enterprise_email;
    }

    public void setEnterprise_email(String enterprise_email) {
        this.enterprise_email = enterprise_email;
    }

    public byte getEnterprise_email_validated() {
        return enterprise_email_validated;
    }

    public void setEnterprise_email_validated(byte enterprise_email_validated) {
        this.enterprise_email_validated = enterprise_email_validated;
    }

    public String getTel() {
        return tel;
    }

    public void setTel(String tel) {
        this.tel = tel;
    }

    public byte getTel_validated() {
        return tel_validated;
    }

    public void setTel_validated(byte tel_validated) {
        this.tel_validated = tel_validated;
    }

    public long getGit_id() {
        return git_id;
    }

    public void setGit_id(long git_id) {
        this.git_id = git_id;
    }

    public String getGit_email() {
        return git_email;
    }

    public void setGit_email(String git_email) {
        this.git_email = git_email;
    }

    public String getGit_pwd() {
        return git_pwd;
    }

    public void setGit_pwd(String git_pwd) {
        this.git_pwd = git_pwd;
    }

    public String getFrom() {
        return from;
    }

    public void setFrom(String from) {
        this.from = from;
    }

    public static byte getONLINE() {
        return ONLINE;
    }

    public static byte getOFFLINE() {
        return OFFLINE;
    }

    public boolean IsBlocked() {
        if(this.getStatus() ==STATUS_DISABLED){
            return true;
        }else {
            return false;
        }
    }
}
