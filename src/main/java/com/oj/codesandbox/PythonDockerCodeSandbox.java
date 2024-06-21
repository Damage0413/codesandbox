package com.oj.codesandbox;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.ExecCreateCmdResponse;
import com.github.dockerjava.api.command.PullImageCmd;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.command.StatsCmd;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.PullResponseItem;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.core.InvocationBuilder.AsyncResultCallback;
import com.github.dockerjava.core.command.ExecStartResultCallback;
import com.oj.codesandbox.model.ExecuteCodeRequest;
import com.oj.codesandbox.model.ExecuteCodeResponse;
import com.oj.codesandbox.model.ExecuteMessage;
import com.oj.codesandbox.model.JudgeInfo;

import cn.hutool.core.date.StopWatch;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.io.resource.ResourceUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.dfa.FoundWord;
import cn.hutool.dfa.WordTree;

@Component
@SuppressWarnings("deprecation")
public class PythonDockerCodeSandbox implements CodeSandbox {
    private static final String GLOBAL_CODE_DIR_NAME = "tmpCode";

    private static final String GLOBAL_PYTHON_NAME = "Main.py";

    private static final long TIME_OUT = 5L;

    private static final Boolean FIRST_INIT = true;

    private static final List<String> blackList = Arrays.asList(
            // 文件操作相关
            "open", "os.system", "os.popen", "os.fdopen", "shutil.copy", "shutil.move", "shutil.rmtree",

            // 网络相关
            "socket", "http.client.HTTPConnection", "http.client.HTTPSConnection", "urllib.request.urlopen",
            "urllib.request.urlretrieve",

            // 系统命令执行相关
            "subprocess.run", "subprocess.Popen",

            // 反射相关
            "__import__", "eval", "exec",

            // 数据库相关
            "sqlite3", "MySQLdb",

            // 加密解密相关
            "cryptography",

            // 序列化相关
            "pickle",

            // 线程相关
            "threading.Thread", "multiprocessing.Process",

            // 安全管理器相关
            "java.lang.SecurityManager",

            // 其他可能导致安全问题的操作
            "ctypes.CDLL", "ctypes.WinDLL", "ctypes.CFUNCTYPE", "os.environ", "os.putenv", "atexit.register",

            // 与操作系统交互
            "os.chmod", "os.chown",

            // 文件权限控制
            "os.access", "os.setuid", "os.setgid",

            // 环境变量操作
            "os.environ['SOME_VAR']", "os.putenv('SOME_VAR', 'value')",

            // 不安全的输入
            "input", "raw_input",

            // 不安全的字符串拼接
            "eval(f'expr {var}')", "var = var1 + var2",

            // 定时器相关
            "time.sleep",

            // 定时任务
            "schedule",

            // 本地文件包含
            "exec(open('filename').read())",

            // 不安全的网站访问
            "urllib.urlopen",

            // 系统退出
            "exit",

            // 其他危险操作
            "os.remove", "os.unlink", "os.rmdir", "os.removedirs", "os.rename", "os.execvp", "os.execlp",

            // 不安全的随机数生成
            "random",

            // 不安全的正则表达式
            "re.compile",

            // 使用 eval 解析 JSON
            "eval(json_string)",

            // 使用 pickle 处理不受信任的数据
            "pickle.loads",

            // 使用 exec 执行不受信任的代码
            "exec(code)",

            // 不安全的 HTML 解析
            "BeautifulSoup",

            // 不安全的 XML 解析
            "xml.etree.ElementTree",

            // 使用自定义反序列化
            "pickle.Unpickler", "marshal.loads",

            // 在代码中直接拼接 SQL
            "sqlalchemy.text",

            // 不安全的图像处理
            "PIL.Image",

            // 使用 ctypes 执行外部 C 代码
            "ctypes.CDLL",

            // 不安全的邮件操作
            "smtplib", "poplib",

            // 不安全的 URL 拼接
            "urllib.parse.urljoin",

            // 使用 eval 执行 JavaScript 代码
            "execjs.eval",

            // 不安全的 Web 框架设置
            "Flask.app.secret_key",

            // 不安全的 API 请求
            "requests.get", "requests.post",

            // 不安全的模板引擎
            "Jinja2.Template",

            // 不安全的数据反序列化
            "pickle.loads", "marshal.loads",

            // 不安全的文件上传
            "werkzeug.FileStorage",

            // 不安全的命令行参数解析
            "argparse.ArgumentParser");

    /**
     * 代码黑名单字典树
     */
    private static final WordTree WORD_TREE;

    static {
        // 初始化黑名单字典树
        WORD_TREE = new WordTree();
        WORD_TREE.addWords(blackList);
    }

    // public static void main(String[] args) {
    // PythonDockerCodeSandbox pythonDockerCodeSandbox = new
    // PythonDockerCodeSandbox();
    // ExecuteCodeRequest executeCodeRequest = new ExecuteCodeRequest();
    // executeCodeRequest.setInputList(Arrays.asList("1 2", "1 3"));
    // String code = ResourceUtil.readStr("testCode/simpleComputeArgs/Main.py",
    // StandardCharsets.UTF_8);
    // executeCodeRequest.setCode(code);
    // executeCodeRequest.setLanguage("python");
    // ExecuteCodeResponse executeCodeResponse =
    // pythonDockerCodeSandbox.executeCode(executeCodeRequest);
    // System.out.println(executeCodeResponse);
    // }

    @Override
    public ExecuteCodeResponse executeCode(ExecuteCodeRequest executeCodeRequest) {

        List<String> inputList = executeCodeRequest.getInputList();
        String code = executeCodeRequest.getCode();

        // 使用字典树筛查提交代码
        FoundWord foundWord = WORD_TREE.matchWord(code);
        if (foundWord != null) {
            System.out.println("包含禁止词：" + foundWord.getFoundWord());
            // 返回错误信息
            return new ExecuteCodeResponse(null, "包含禁止词：" + foundWord.getFoundWord(),
                    3,
                    new JudgeInfo("包含禁止词：" + foundWord.getFoundWord(), 0l, 0l));
        }

        // 1）将用户提交的代码保存为文件
        String userDir = System.getProperty("user.dir");
        String globalCodePathName = userDir + File.separator + GLOBAL_CODE_DIR_NAME;
        if (!FileUtil.exist(globalCodePathName)) {
            // 文件夹不存在，新建文件夹
            FileUtil.mkdir(globalCodePathName);
        }

        // 把用户的代码隔离存放
        String userCodeParentPath = globalCodePathName + File.separator + UUID.randomUUID();
        String userCodePath = userCodeParentPath + File.separator + GLOBAL_PYTHON_NAME;
        File userCodeFile = FileUtil.writeString(code, userCodePath, StandardCharsets.UTF_8);

        // 2）不用编译，直接创建Docker容器
        DockerClient dockerClient = DockerClientBuilder.getInstance().build();

        // 拉取镜像
        String image = "python:3.8-alpine";
        if (FIRST_INIT) {
            PullImageCmd pullImageCmd = dockerClient.pullImageCmd(image);
            PullImageResultCallback pullImageResultCallback = new PullImageResultCallback() {
                @Override
                public void onNext(PullResponseItem item) {
                    System.out.println("下载镜像：" + item.getStatus());
                    super.onNext(item);
                }
            };
            try {
                pullImageCmd
                        .exec(pullImageResultCallback)
                        .awaitCompletion();
            } catch (InterruptedException e) {
                System.out.println("拉取镜像异常");
                throw new RuntimeException(e);
            }
        }
        System.out.println("下载完成");

        // 创建容器
        CreateContainerCmd containerCmd = dockerClient.createContainerCmd(image);
        // 此处使用配置信息进行代码沙箱的安全设置
        HostConfig hostConfig = new HostConfig();
        // 限制内存大小
        hostConfig.withMemory(100 * 1000 * 1000l);
        hostConfig.withMemorySwap(0L);
        hostConfig.withCpuCount(1L);

        // 指定文件路径映射，将编译好的文件映射到容器中
        hostConfig.setBinds(new Bind(userCodeParentPath, new Volume("/app")));

        // 此处限制容器的网络功能和写功能
        CreateContainerResponse createContainerResponse = containerCmd.withHostConfig(hostConfig)
                .withNetworkDisabled(true)
                .withReadonlyRootfs(true)
                .withAttachStdin(true)
                .withAttachStderr(true)
                .withAttachStdout(true)
                .withTty(true)
                .exec();
        System.out.println("创建容器：" + createContainerResponse);
        // 获取创建的容器的id
        String containerId = createContainerResponse.getId();

        // 启动容器
        dockerClient.startContainerCmd(containerId).exec();

        // docker exec keen_blackwell python /app/Main.py 1 2
        List<ExecuteMessage> executeMessages = new ArrayList<>();
        for (String inputArgs : inputList) {
            StopWatch stopWatch = new StopWatch();
            String[] inputArgsArray = inputArgs.split(" ");
            String[] cmdArray = ArrayUtil.append(new String[] { "python3", "/app/Main.py" }, inputArgsArray);
            System.out.println("指令：" + cmdArray);
            // 向容器输入指令
            ExecCreateCmdResponse execCreateCmdResponse = dockerClient.execCreateCmd(containerId)
                    .withCmd(cmdArray)
                    .withAttachStderr(true)
                    .withAttachStdin(true)
                    .withAttachStdout(true)
                    .exec();
            System.out.println("创建执行命令：" + execCreateCmdResponse);

            ExecuteMessage executeMessage = new ExecuteMessage();
            final String[] message = { null };
            final String[] errorMessage = { null };
            long time = 0L;
            // 判断是否超时以及使用的内存
            final boolean[] timeout = { true };
            final long[] maxMemory = { 0L };
            String execId = execCreateCmdResponse.getId();
            ExecStartResultCallback execStartResultCallback = new ExecStartResultCallback() {

                @Override
                public void onNext(Frame frame) {
                    StreamType streamType = frame.getStreamType();
                    if (streamType.equals(StreamType.STDERR)) {
                        errorMessage[0] = new String(frame.getPayload());
                        System.out.println("输出错误结果：" + errorMessage[0]);
                    } else if (streamType.equals(StreamType.STDOUT)) {
                        message[0] = new String(frame.getPayload());
                        System.out.println("输出结果：" + message[0]);
                    } else {
                        System.out.println("出错了");
                    }
                    super.onNext(frame);
                }

                @Override
                public void onComplete() {
                    timeout[0] = false;
                    super.onComplete();
                }
            };

            AsyncResultCallback<Statistics> asyncResultCallback = new AsyncResultCallback<Statistics>() {

                @Override
                public void onNext(Statistics object) {
                    System.out.println("内存占用：" + object.getMemoryStats().getUsage());
                    maxMemory[0] = Math.max(object.getMemoryStats().getUsage(), maxMemory[0]);
                }

            };
            StatsCmd statsCmd = dockerClient.statsCmd(containerId);
            try {
                System.out.println("开始执行");
                statsCmd.exec(asyncResultCallback);
                stopWatch.start();
                dockerClient.execStartCmd(execId)
                        .exec(execStartResultCallback)
                        .awaitCompletion(TIME_OUT, TimeUnit.SECONDS);
                stopWatch.stop();
                time = stopWatch.getLastTaskTimeMillis();
                statsCmd.close();
                System.out.println("执行结束");
            } catch (InterruptedException e) {
                System.out.println("程序执行异常");
                throw new RuntimeException(e);
            }
            executeMessage.setTime(time);
            executeMessage.setMessage(message[0]);
            executeMessage.setErrorMessage(errorMessage[0]);
            System.out.println("内存为：" + maxMemory[0]);
            executeMessage.setMemory(maxMemory[0]);
            executeMessages.add(executeMessage);
        }
        // 4、封装结果，跟原生实现方式完全一致
        ExecuteCodeResponse executeCodeResponse = new ExecuteCodeResponse();
        List<String> outputList = new ArrayList<>();
        // 取用时最大值，便于判断是否超时以及是否超出内存限制
        long maxTime = 0;
        long maxMemory = 0L;
        for (ExecuteMessage executeMessage : executeMessages) {
            String errorMessage = executeMessage.getErrorMessage();
            if (StrUtil.isNotBlank(errorMessage)) {
                executeCodeResponse.setMessage(errorMessage);
                // 用户提交的代码执行中存在错误
                executeCodeResponse.setStatus(3);
                break;
            }
            outputList.add(executeMessage.getMessage());
            Long time = executeMessage.getTime();
            if (time != null) {
                maxTime = Math.max(maxTime, time);
            }
            Long memory = executeMessage.getMemory();
            if (memory != null) {
                maxMemory = Math.max(maxMemory, memory);
            }
        }
        // 正常运行完成
        if (outputList.size() == executeMessages.size()) {
            executeCodeResponse.setStatus(1);
        }
        executeCodeResponse.setOutputList(outputList);
        JudgeInfo judgeInfo = new JudgeInfo();
        judgeInfo.setTime(maxTime);
        judgeInfo.setMemory(maxMemory);

        executeCodeResponse.setJudgeInfo(judgeInfo);

        // 5. 文件清理
        if (userCodeFile.getParentFile() != null) {
            boolean del = FileUtil.del(userCodeParentPath);
            System.out.println("删除" + (del ? "成功" : "失败"));
        }
        return executeCodeResponse;
    }

}
