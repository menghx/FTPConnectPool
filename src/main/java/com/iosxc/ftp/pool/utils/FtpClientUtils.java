package com.iosxc.ftp.pool.utils;

import com.iosxc.ftp.pool.config.FtpClientProperties;
import com.iosxc.ftp.pool.core.FtpClientFactory;
import com.iosxc.ftp.pool.core.FtpClientPoolManager;
import org.apache.commons.io.IOUtils;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;

/**
 *
 * @author Crazz
 */
public class FtpClientUtils {

    private static Logger log = LoggerFactory.getLogger(FtpClientUtils.class);

    private static GenericObjectPool<FTPClient> ftpClientPool = FtpClientPoolManager.getPool();

    private static boolean isInit = false;

    public static void initFactory(FtpClientProperties properties){
        FtpClientPoolManager.initFactory(new FtpClientFactory(properties));
        isInit = true;
    }

    /***
     * 上传Ftp文件
     *
     * @param localFile 当地文件
     * @param remotePath 上传服务器路径 - 应该以/结束
     * @return true or false
     */
    public static boolean uploadFile(File localFile, String remotePath) {
        if (!isInit) {
            log.error("Please init ftp factory first! @see FtpClientUtils.initFactory(FtpClientProperties properties)");
            return false;
        }
        FTPClient ftpClient = null;
        BufferedInputStream inStream = null;
        try {
            //从池中获取对象
            ftpClient = ftpClientPool.borrowObject();
            // 验证FTP服务器是否登录成功
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                log.warn("ftpServer refused connection, replyCode:{}", replyCode);
                return false;
            }
            // 改变工作路径
            ftpClient.changeWorkingDirectory(remotePath);
            inStream = new BufferedInputStream(new FileInputStream(localFile));
            log.info("start upload... {}", localFile.getName());

            final int retryTimes = 3;

            for (int j = 0; j <= retryTimes; j++) {
                boolean success = ftpClient.storeFile(localFile.getName(), inStream);
                if (success) {
                    log.info("upload file success! {}", localFile.getName());
                    return true;
                }
                log.warn("upload file failure! try uploading again... {} times", j);
            }

        } catch (FileNotFoundException e) {
            log.error("file not found!{}", localFile);
        } catch (Exception e) {
            log.error("upload file failure!", e);
        } finally {
            IOUtils.closeQuietly(inStream);
            ftpClientPool.returnObject(ftpClient);
        }
        return false;
    }

    /**
     * 下载文件
     *
     * @param remotePath FTP服务器文件目录
     * @param fileName   需要下载的文件名称
     * @param localPath  下载后的文件路径
     * @return true or false
     */
    public  static boolean downloadFile(String remotePath, String fileName, String localPath) {
        if (!isInit) {
            log.error("Please init ftp factory first! @see FtpClientUtils.initFactory(FtpClientProperties properties)");
            return false;
        }
        FTPClient ftpClient = null;
        OutputStream outputStream = null;
        try {
            ftpClient = ftpClientPool.borrowObject();
            // 验证FTP服务器是否登录成功
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                log.warn("ftpServer refused connection, replyCode:{}", replyCode);
                return false;
            }

            // 切换FTP目录
            ftpClient.changeWorkingDirectory(remotePath);
            FTPFile[] ftpFiles = ftpClient.listFiles();
            for (FTPFile file : ftpFiles) {
                if (fileName.equalsIgnoreCase(file.getName())) {
                    StringBuilder stringBuilder = new StringBuilder();
                    stringBuilder.append(localPath).append(File.separator).append(file.getName());
                    File localFile = new File(stringBuilder.toString());
                    outputStream = new FileOutputStream(localFile);
                    ftpClient.retrieveFile(file.getName(), outputStream);
                }
            }
            ftpClient.logout();
            return true;
        } catch (Exception e) {
            log.error("download file failure!", e);
        } finally {
            IOUtils.closeQuietly(outputStream);
            ftpClientPool.returnObject(ftpClient);
        }
        return false;
    }

    /**
     * 删除文件
     *
     * @param remotePath FTP服务器保存目录
     * @param fileName   要删除的文件名称
     * @return true or false
     */
    public static boolean deleteFile(String remotePath, String fileName) {
        if (!isInit) {
            log.error("Please init ftp factory first! @see FtpClientUtils.initFactory(FtpClientProperties properties)");
            return false;
        }
        FTPClient ftpClient = null;
        try {
            ftpClient = ftpClientPool.borrowObject();
            // 验证FTP服务器是否登录成功
            int replyCode = ftpClient.getReplyCode();
            if (!FTPReply.isPositiveCompletion(replyCode)) {
                log.warn("ftpServer refused connection, replyCode:{}", replyCode);
                return false;
            }
            // 切换FTP目录
            ftpClient.changeWorkingDirectory(remotePath);
            int delCode = ftpClient.dele(fileName);
            log.debug("delete file reply code:{}", delCode);
            return true;
        } catch (Exception e) {
            log.error("delete file failure!", e);
        } finally {
            ftpClientPool.returnObject(ftpClient);
        }
        return false;
    }


}