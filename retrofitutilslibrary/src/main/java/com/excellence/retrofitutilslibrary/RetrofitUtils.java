package com.excellence.retrofitutilslibrary;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.excellence.retrofitutilslibrary.interfaces.DownloadListener;
import com.excellence.retrofitutilslibrary.interfaces.Error;
import com.excellence.retrofitutilslibrary.interfaces.Success;
import com.excellence.retrofitutilslibrary.utils.OkHttpProvider;
import com.excellence.retrofitutilslibrary.utils.Utils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

import okhttp3.OkHttpClient;
import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.CallAdapter;
import retrofit2.Callback;
import retrofit2.Converter;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.scalars.ScalarsConverterFactory;

import static com.excellence.retrofitutilslibrary.utils.DownloadInterceptor.DOWNLOAD;
import static com.excellence.retrofitutilslibrary.utils.Utils.checkHeaders;
import static com.excellence.retrofitutilslibrary.utils.Utils.checkParams;
import static com.excellence.retrofitutilslibrary.utils.Utils.checkURL;
import static com.excellence.retrofitutilslibrary.utils.Utils.isURLEmpty;
import static java.net.HttpURLConnection.HTTP_OK;

/**
 * <pre>
 *     author : VeiZhang
 *     blog   : https://veizhang.github.io/
 *     time   : 2017/4/7
 *     desc   : Retrofit封装
 *     			<ul>
 *     			   <li>get请求封装</li>
 *     			   <li>参数可配置</li>
 *     			   <li>异步统一回调接口</li>
 *     			   <li>单个界面所有请求取消</li>
 *     			   <li>缓存策略</li>
 *     			   <li>下载</li>
 *     			</ul>
 * </pre>
 */

public class RetrofitUtils
{
	public static final String TAG = RetrofitUtils.class.getSimpleName();

	private RetrofitHttpService mService = null;
	private String mBaseUrl = null;
	private OkHttpClient mClient = null;

	/**
	 * 请求参数
	 */
	private Map<String, String> mParams = new HashMap<>();

	/**
	 * 请求头
	 */
	private Map<String, String> mHeaders = new HashMap<>();

	/**
	 * 网络请求队列
	 */
	private static final Map<String, Call> CALL_MAP = new HashMap<>();

	/**
	 * 网络请求标识
	 */
	private Object mTag = null;

	private Executor mResponseposter = null;

	private RetrofitUtils(RetrofitHttpService service, String baseUrl, OkHttpClient client, Executor responsePoster)
	{
		mService = service;
		mBaseUrl = baseUrl;
		mClient = client;
		mResponseposter = responsePoster;
	}

	public RetrofitHttpService getService()
	{
		return mService;
	}

	public String getBaseUrl()
	{
		return mBaseUrl;
	}

	public OkHttpClient getClient()
	{
		return mClient;
	}

	public static class Builder
	{
		private Context mContext = null;
		private String mBaseUrl = null;
		private OkHttpClient mClient = null;
		private List<Converter.Factory> mConverterFactories = new ArrayList<>();
		private List<CallAdapter.Factory> mCallAdapterFactories = new ArrayList<>();
		private Executor mResponsePoster = null;

		public Builder(@NonNull Context context)
		{
			mContext = context;
		}

		public Builder baseUrl(@NonNull String baseUrl)
		{
			mBaseUrl = baseUrl;
			return this;
		}

		public Builder client(@NonNull OkHttpClient client)
		{
			mClient = client;
			return this;
		}

		public Builder addConverterFactory(@NonNull Converter.Factory factory)
		{
			mConverterFactories.add(factory);
			return this;
		}

		public Builder addCallAdapterFactory(@NonNull CallAdapter.Factory factory)
		{
			mCallAdapterFactories.add(factory);
			return this;
		}

		public RetrofitUtils build()
		{
			checkURL(mBaseUrl);
			if (!mBaseUrl.endsWith("/"))
				mBaseUrl += "/";

			if (mClient == null)
			{
				mClient = OkHttpProvider.okHttpClient(mContext);
			}

			if (mConverterFactories.isEmpty())
				mConverterFactories.add(ScalarsConverterFactory.create());

			if (mResponsePoster == null)
			{
				final Handler handler = new Handler(Looper.getMainLooper());
				mResponsePoster = new Executor()
				{
					@Override
					public void execute(@NonNull Runnable command)
					{
						handler.post(command);
					}
				};
			}

			Retrofit.Builder builder = new Retrofit.Builder();
			builder.baseUrl(mBaseUrl);
			builder.client(mClient);
			for (Converter.Factory converterFactory : mConverterFactories)
				builder.addConverterFactory(converterFactory);
			for (CallAdapter.Factory callAdapterFactory : mCallAdapterFactories)
				builder.addCallAdapterFactory(callAdapterFactory);
			Retrofit retrofit = builder.build();
			RetrofitHttpService service = retrofit.create(RetrofitHttpService.class);
			return new RetrofitUtils(service, mBaseUrl, mClient, mResponsePoster);
		}
	}

	public RetrofitUtils setParam(String key, String value)
	{
		mParams.put(key, value);
		return this;
	}

	public RetrofitUtils setParams(Map<String, String> params)
	{
		mParams.putAll(params);
		return this;
	}

	public RetrofitUtils setHeader(String key, String value)
	{
		mHeaders.put(key, value);
		return this;
	}

	public RetrofitUtils setHeaders(Map<String, String> headers)
	{
		mHeaders.putAll(headers);
		return this;
	}

	public void get(@NonNull final String url, @NonNull final Success successCall, @NonNull final Error errorCall)
	{
		Call call = mService.get(checkURL(url), checkParams(mParams), checkHeaders(mHeaders));
		addCall(mTag, url, call);
		call.enqueue(new Callback<String>()
		{
			@Override
			public void onResponse(Call<String> call, Response<String> response)
			{
				if (response.code() == HTTP_OK)
				{
					successCall.success(response.body());
				}
				else
				{
					String errorMsg = Utils.inputStream2String(response.errorBody().byteStream());
					if (!TextUtils.isEmpty(errorMsg))
						errorCall.error(new Throwable(errorMsg));
					else
					{
						// 离线时使用缓存出现异常，如果没有上次缓存，出现异常时是没有打印信息的，添加自定义异常信息方便识别
						errorCall.error(new Throwable("There may be no cache data!"));
					}
				}
				if (mTag != null)
					removeCall(url);
			}

			@Override
			public void onFailure(Call<String> call, Throwable t)
			{
				if (!call.isCanceled())
					errorCall.error(t);
				if (mTag != null)
					removeCall(url);
			}
		});
	}

	public void download(@NonNull final String url, @NonNull final String path, @NonNull final DownloadListener listener)
	{
		mHeaders.put(DOWNLOAD, DOWNLOAD);
		Call<ResponseBody> call = mService.download(checkURL(url), checkParams(mParams), checkHeaders(mHeaders));
		addCall(mTag, url, call);
		call.enqueue(new Callback<ResponseBody>()
		{
			@Override
			public void onResponse(Call<ResponseBody> call, final Response<ResponseBody> response)
			{
				if (response.code() == HTTP_OK)
				{
					new AsyncTask<Void, Long, Void>()
					{
						@Override
						protected Void doInBackground(Void... params)
						{
							writetoFile(listener, path, response.body());
							if (mTag != null)
								removeCall(url);
							return null;
						}

					}.execute();
				}
				else
				{
					String errorMsg = Utils.inputStream2String(response.errorBody().byteStream());
					listener.onError(new Throwable(errorMsg));
					if (mTag != null)
						removeCall(url);
				}
			}

			@Override
			public void onFailure(Call<ResponseBody> call, Throwable t)
			{
				if (!call.isCanceled())
					listener.onError(t);
				if (mTag != null)
					removeCall(url);
			}
		});
	}

	private void writetoFile(DownloadListener listener, String path, ResponseBody response)
	{
		File file = new File(path);
		InputStream in = null;
		OutputStream out = null;
		try
		{
			long fileSize = response.contentLength();
			long downloadedSize = 0;
			int read = 0;
			byte[] fileReader = new byte[1024 * 4];
			onPreExecute(listener, fileSize);
			in = response.byteStream();
			out = new FileOutputStream(file);
			while (true)
			{
				read = in.read(fileReader);
				if (read == -1)
					break;
				out.write(fileReader, 0, read);
				downloadedSize += read;
				onProgressChange(listener, fileSize, downloadedSize);
			}
			onSuccess(listener);
		}
		catch (Exception e)
		{
			onError(listener, e);
		}
		finally
		{
			try
			{
				if (in != null)
					in.close();
				if (out != null)
					out.close();
			}
			catch (IOException e)
			{
				e.printStackTrace();
			}
		}
	}

	private void onPreExecute(final DownloadListener listener, final long fileSize)
	{
		mResponseposter.execute(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onPreExecute(fileSize);
			}
		});
	}

	private void onProgressChange(final DownloadListener listener, final long fileSize, final long downloadedSize)
	{
		mResponseposter.execute(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onProgressChange(fileSize, downloadedSize);
			}
		});
	}

	private void onSuccess(final DownloadListener listener)
	{
		mResponseposter.execute(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onSuccess();
			}
		});
	}

	private void onError(final DownloadListener listener, final Exception e)
	{
		mResponseposter.execute(new Runnable()
		{
			@Override
			public void run()
			{
				listener.onError(e);
			}
		});
	}

	public RetrofitUtils setTag(Object tag)
	{
		mTag = tag;
		return this;
	}

	/**
	 * 添加网络请求队列，以 tag + url 作为标识
	 *
	 * @param tag 标签
	 * @param url 请求链接
	 * @param call 网络请求
	 */
	private synchronized void addCall(Object tag, String url, Call call)
	{
		if (tag == null)
			return;
		synchronized (CALL_MAP)
		{
			CALL_MAP.put(tag.toString() + url, call);
		}
	}

	/**
	 * 取消单个网络请求
	 *
	 * @param url 请求链接
	 */
	private static synchronized void removeCall(String url)
	{
		synchronized (CALL_MAP)
		{
			for (String key : CALL_MAP.keySet())
			{
				if (key.contains(url))
				{
					url = key;
					break;
				}
			}
			CALL_MAP.remove(url);
		}
	}

	/**
	 * 取消单个界面的所有请求，或取消某个tag的所有请求
	 *
	 * @param tag 标签
	 */
	public static synchronized void cancel(Object tag)
	{
		if (tag == null)
			return;
		synchronized (CALL_MAP)
		{
			for (String key : CALL_MAP.keySet())
			{
				if (key.startsWith(tag.toString()))
				{
					CALL_MAP.get(key).cancel();
					removeCall(key);
				}
			}
		}

	}

}
