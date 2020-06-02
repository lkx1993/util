package com.liukx;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.filters.Watermark;
import net.coobird.thumbnailator.geometry.Position;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.imageio.ImageIO;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import javax.servlet.http.HttpServletRequest;
import java.beans.PropertyDescriptor;
import java.io.File;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.ParseException;
import java.util.*;

/**
 * @author liukx
 * @since 2020-06-01 14:31
 **/
public class AppUtil {

    private static final Logger logger = LoggerFactory.getLogger(AppUtil.class);

    public static String getUUID() {
        UUID uuid = UUID.randomUUID();
        String uuidStr = uuid.toString();
        if (uuidStr.length() <= 32) {
            return uuidStr;
        } else {
            return uuidStr.replace("-", "");
        }
    }

    public static Map<String, Object> entityToMap(Object object) {
        return (JSONObject)JSON.toJSON(object);
    }

    private static final String[] IP_HEADER_CANDIDATES = {
            "X-Forwarded-For",
            "Proxy-Client-IP",
            "WL-Proxy-Client-IP",
            "HTTP_X_FORWARDED_FOR",
            "HTTP_X_FORWARDED",
            "HTTP_X_CLUSTER_CLIENT_IP",
            "HTTP_CLIENT_IP",
            "HTTP_FORWARDED_FOR",
            "HTTP_FORWARDED",
            "HTTP_VIA",
            "REMOTE_ADDR" };

    /**
     * 获取客户端的Ip地址
     *
     * @return the client ip address
     */
    public static String getClientIpAddress(){
        HttpServletRequest request = getHttpServletRequest();

        for (String header : IP_HEADER_CANDIDATES) {
            String ip = request.getHeader(header);
            if (StringUtils.isNotEmpty(ip) && !"unknown".equalsIgnoreCase(ip)) {
                return StringUtils.substringBefore(ip,",");
            }
        }
        return request.getRemoteAddr();
    }

    public static HttpServletRequest getHttpServletRequest(){
        RequestAttributes requestAttributes = RequestContextHolder.currentRequestAttributes();
        if(requestAttributes != null && requestAttributes instanceof ServletRequestAttributes){
            HttpServletRequest servletRequest = ((ServletRequestAttributes)requestAttributes).getRequest();
            return servletRequest;
        }
        return null;
    }

    /**
     * @description 获取异常的堆栈信息
     * @author liukx
     * @date 2019/8/3
     */
    public static String getStackTrace(Throwable t){
        if(t == null){
            return null;
        }

        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        try {
            t.printStackTrace(pw);
        }finally {
            pw.close();
        }
        return sw.toString();
    }


    public static Date parseDate(String dateStr, String... parsePatterns){
        try {
            return DateUtils.parseDate(dateStr, parsePatterns);
        } catch (ParseException e) {
            logger.error("时间解析异常:{},{}",dateStr,parsePatterns);
        }
        return null;
    }

    public static Date parseDate(String dateStr){
        return parseDate(dateStr,"yyyy-MM-dd HH:mm:ss","yyyy-MM-dd");
    }

    public static String formatDate(Date date){
        if(date == null){
            return null;
        }
        return DateFormatUtils.format(date,"yyyy-MM-dd");
    }

    public static String formatDateTime(Date date){
        if(date == null){
            return null;
        }
        return DateFormatUtils.format(date,"yyyy-MM-dd HH:mm:ss");
    }

    /**
     * @description 获取对象的非空属性,方便copy
     * @author liukx
     * @date 2018-12-07
     */
    public static String[] getNullPropertyNames(Object source,String... ignoreProperties) {
        final BeanWrapper src = new BeanWrapperImpl(source);
        PropertyDescriptor[] pds = src.getPropertyDescriptors();

        List<String> ignoreList = ignoreProperties == null?null:Arrays.asList(ignoreProperties);

        Set<String> emptyNames = new HashSet<String>();
        for(PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null && (ignoreList == null || !ignoreList.contains(pd.getName()))){
                emptyNames.add(pd.getName());
            }
        }
        String[] result = new String[emptyNames.size()];
        return emptyNames.toArray(result);
    }

    public static void sleep(int millis) {
        try{
            Thread.sleep(millis);
        }catch(Exception e){
            throw new ServiceException("休眠失败",e);
        }
    }

    /**
     * @description 报表文件的时间:2018-06-27T22:22:54+00:00,
     * 后面的时区现在在变化2018-06-28T21:15:52-07:00,统一解析成utc时间
     * //todo 有这种时间格式可以直接解析的
     * like  Jackson2ObjectMapperBuilder b = new Jackson2ObjectMapperBuilder();
     *         b.indentOutput(true).dateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss,SSS X")).timeZone("GMT+8");
     * @author liukx
     * @date 2018-07-31
     */
    public static Date parseReportDateStringToUTC(final String dateString){
        String str =  dateString.substring(0,19).replace("T"," ");
        Date date = parseDate(str);
        int offset = -1*Integer.parseInt(dateString.substring(19,22));
        Date utcDate = DateUtils.addHours(date,offset);
        return utcDate;
    }

    /**
     * @description e是 FeignException
     * 可以参考
     * @author liukx
     * @date 2020/5/8 0008
     */
    public static ServiceException convertFeignExceptionToServiceException(Exception feignException){
        if(feignException == null){
            return null;
        }
        String str = feignException.getMessage();
        str = StringUtils.substringAfter(str,"); content:\n");
        return new ServiceException(str);
    }

    /**
     * @description 给图片加水印
     * @param watermarkIn 水印
     * @param watermarkPosition 水印位置
     * @param img 需要添加水印的图片
     * @param outPutImg 输出的图片
     * @author liukx
     * @date 2020/5/11 0011
     */
    public static void addImgWatermark(InputStream watermarkIn, Position watermarkPosition, File img, File outPutImg) {
        addImgWatermark(watermarkIn, watermarkPosition, 0.5f, img, outPutImg, 1.0f, 1.0f);
    }

    /**
     * @description 给图片加水印
     * @param watermarkIn 水印
     * @param watermarkPosition 水印位置
     * @param watermarkOpacity 水印透明度
     * @param img 需要添加水印的图片
     * @param outPutImg 输出的图片
     * @param outPutImgScale 输出图片缩放比例
     * @param outPutImgQuality 输出图片的质量
     * @author liukx
     * @date 2020/5/11 0011
     */
    public static void addImgWatermark(InputStream watermarkIn, Position watermarkPosition, float watermarkOpacity, File img,File outPutImg,
                                       float outPutImgScale,float outPutImgQuality){
        try {
            Watermark watermark = new Watermark(watermarkPosition, ImageIO.read(watermarkIn), watermarkOpacity);
            Thumbnails.of(img)
                    .scale(outPutImgScale)
                    .outputQuality(outPutImgQuality)
                    .watermark(watermark)
                    .toFile(outPutImg);
        }catch (Exception e){
            logger.error("给图片加水印出现异常,img:{},outPutImg:{}",img.getAbsolutePath(),outPutImg.getAbsolutePath(),e);
        }finally {
            IOUtils.closeQuietly(watermarkIn);
        }
    }

    /**
     * @description org.springframework.beans.BeanUtils copy属性到vo并返回
     * ...写法支持 copyPropertiesToVo(Object source, T vo)
     * @author liukx
     * @date 2020/5/29 0029
     */
    public static <T> T copyPropertiesToVo(Object source, T vo,String... ignoreProperties) {
        BeanUtils.copyProperties(source,vo,ignoreProperties);
        return vo;
    }

    /**
     * @description 分页查询toPredicate的优化,快速生成简单的and equal查询
     * @author liukx
     * @date 2020/6/1 0001
     */
    public static Predicate toAndEqualPredicate(Predicate predicate, Root root, CriteriaBuilder cb, Object obj, String... ignoreProperties){
        final BeanWrapper src = new BeanWrapperImpl(obj);

        List<String> ignoreList =  ArrayUtils.isEmpty(ignoreProperties) ?new ArrayList<>():Arrays.asList(ignoreProperties);

        PropertyDescriptor[] pds = src.getPropertyDescriptors();
        for(PropertyDescriptor pd : pds) {
            Object srcValue = src.getPropertyValue(pd.getName());
            if (srcValue == null || ignoreList.contains(pd.getName()) || srcValue.toString().length() == 0){
                continue;
            }
            predicate = cb.and(predicate, cb.equal(root.get(pd.getName()), srcValue));
        }
        return predicate;
    }

}