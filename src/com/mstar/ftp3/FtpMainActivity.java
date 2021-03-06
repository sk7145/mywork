package com.mstar.ftp3;

import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPDataTransferListener;
import it.sauronsoftware.ftp4j.FTPException;
import it.sauronsoftware.ftp4j.FTPFile;
import it.sauronsoftware.ftp4j.FTPIllegalReplyException;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.mstar.ftp3.adapter.FtpFileAdapter;
import com.mstar.ftp3.adapter.UploadFileChooserAdapter;
import com.mstar.ftp3.adapter.UploadFileChooserAdapter.FileInfo;
import com.mstar.ftp3.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FtpMainActivity extends Activity implements OnClickListener {

	private static String TAG = FtpMainActivity.class.getName();

	private CmdFactory mCmdFactory;
	private FTPClient mFTPClient;
	private ExecutorService mThreadPool;

	private static String mPath;

	private ProgressBar mPbLoad = null;
	
	
	private ListView mListView;
	private FtpFileAdapter mAdapter;
	private List<FTPFile> mFileList = new ArrayList<FTPFile>();
	private FTPFile[] datas;
	
	private Object mLock = new Object();
	private int mSelectedPosistion = -1;

	private String mCurrentPWD; 
	private boolean isBack = false;
	
	private static final String OLIVE_DIR_NAME = "OliveDownload";

	// 上传
	private GridView mGridView;
	private View fileChooserView;
	private TextView mTvPath;
	private String mLocalRootPath;
	private String mLastFilePath;
	private List<FileInfo> mUploadFileList;
	private UploadFileChooserAdapter mUploadAdapter;
	//

	private Dialog progressDialog;
	private Dialog uploadDialog;

	private Thread mDameonThread = null ;
	private boolean mDameonRunning = true;
	
	private String mFTPHost ;
	private int mFTPPort ;
	private String mFTPUser ;
	private String mFTPPassword ;
	
	private static final int MAX_THREAD_NUMBER = 5;
	private static final int MAX_DAMEON_TIME_WAIT = 2 * 1000; // ms

	private static final int MENU_OPTIONS_BASE = 0;
	private static final int MSG_CMD_CONNECT_OK = MENU_OPTIONS_BASE + 1;
	private static final int MSG_CMD_CONNECT_FAILED = MENU_OPTIONS_BASE + 2;
	private static final int MSG_CMD_LIST_OK = MENU_OPTIONS_BASE + 3;
	private static final int MSG_CMD_LIST_FAILED = MENU_OPTIONS_BASE + 4;
	private static final int MSG_CMD_CWD_OK = MENU_OPTIONS_BASE + 5;
	private static final int MSG_CMD_CWD_FAILED = MENU_OPTIONS_BASE + 6;
	private static final int MSG_CMD_DELE_OK = MENU_OPTIONS_BASE + 7;
	private static final int MSG_CMD_DELE_FAILED = MENU_OPTIONS_BASE + 8;
	private static final int MSG_CMD_RENAME_OK = MENU_OPTIONS_BASE + 9;
	private static final int MSG_CMD_RENAME_FAILED = MENU_OPTIONS_BASE + 10;

	private static final int MENU_OPTIONS_DOWNLOAD = MENU_OPTIONS_BASE + 20;
	private static final int MENU_OPTIONS_RENAME = MENU_OPTIONS_BASE + 21;
	private static final int MENU_OPTIONS_DELETE = MENU_OPTIONS_BASE + 22;
	private static final int MENU_DEFAULT_GROUP = 0;

	private static final int DIALOG_LOAD = MENU_OPTIONS_BASE + 40;
	private static final int DIALOG_RENAME = MENU_OPTIONS_BASE + 41;
	private static final int DIALOG_FTP_LOGIN = MENU_OPTIONS_BASE + 42;
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		initView();
		registerForContextMenu(mListView);
		
		mLocalRootPath = Environment.getExternalStorageDirectory().getAbsolutePath();
		mCmdFactory = new CmdFactory();
		mFTPClient = new FTPClient();
		mThreadPool = Executors.newFixedThreadPool(MAX_THREAD_NUMBER);
		
        showDialog(DIALOG_FTP_LOGIN);
	}

	private void initView() {
		mListView = (ListView) findViewById(R.id.listviewApp);

		mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> adapterView, View view,
					int positioin, long id) {
				if (mFileList.get(positioin).getType() == FTPFile.TYPE_DIRECTORY) {
					executeCWDRequest(mFileList.get(positioin).getName());
				}
			}
		});

		mListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {

					@Override
					public boolean onItemLongClick(AdapterView<?> adapterView,
							View view, int positioin, long id) {
						mSelectedPosistion = positioin;
						return false;
					}
				});

		mListView.setOnCreateContextMenuListener(new View.OnCreateContextMenuListener() {

					@Override
					public void onCreateContextMenu(ContextMenu menu, View v,
							ContextMenuInfo menuInfo) {
						Log.v(TAG, "onCreateContextMenu ");
					}

				});
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		if (v.getId() == R.id.listviewApp) {
			menu.setHeaderTitle("文件操作");
			menu.add(MENU_DEFAULT_GROUP, MENU_OPTIONS_DOWNLOAD, Menu.NONE, "下载");
			menu.add(MENU_DEFAULT_GROUP, MENU_OPTIONS_RENAME, Menu.NONE, "重命名");
			menu.add(MENU_DEFAULT_GROUP, MENU_OPTIONS_DELETE, Menu.NONE, "删除");
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		if (mSelectedPosistion < 0 || mFileList.size() < 0) {
			return false;
		}
		AdapterContextMenuInfo menuInfo = (AdapterContextMenuInfo) item.getMenuInfo();
		switch (item.getItemId()) {
		case MENU_OPTIONS_DOWNLOAD:
			if (mFileList.get(mSelectedPosistion).getType() == FTPFile.TYPE_FILE) {
				showDialog(DIALOG_LOAD);
				//下载文件
				new CmdDownLoad().execute();
			} else {
				showDialog(DIALOG_LOAD);
				//下载文件夹
				new CmdDownloadFolder().execute();
			}
			break;
		case MENU_OPTIONS_RENAME:
			showDialog(DIALOG_RENAME);
			break;
		case MENU_OPTIONS_DELETE:
			executeDELERequest(
			mFileList.get(mSelectedPosistion).getName(),
			mFileList.get(mSelectedPosistion).getType() == FTPFile.TYPE_DIRECTORY);

			break;
		default:
			return super.onContextItemSelected(item);
		}
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// TODO Auto-generated method stub
		switch (item.getItemId()) {
		case R.id.menu_updownload:
			openFileDialog();
			return true;
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	protected Dialog onCreateDialog(int id) {
		switch (id) {
		case DIALOG_LOAD:
			return createLoadDialog();
		case DIALOG_RENAME:
			return createRenameDialog();
		case DIALOG_FTP_LOGIN :
			return createFTPLoginDialog();
		default:
			return null;
		}
	}

	private Dialog createFTPLoginDialog() {

		View rootLoadView = getLayoutInflater().inflate(R.layout.dialog_ftp_login,
				null);
		final EditText editHost = (EditText) rootLoadView.findViewById(R.id.editFTPHost);
		final EditText editPort= (EditText) rootLoadView.findViewById(R.id.editFTPPort);
		editPort.setText("2121");
		final EditText editUser = (EditText) rootLoadView.findViewById(R.id.editFTPUser);
		final EditText editPasword= (EditText) rootLoadView.findViewById(R.id.editPassword);
		return new AlertDialog.Builder(this)
				.setTitle("请输入FTP信息")
				.setView(rootLoadView)
				.setPositiveButton("连接FTP", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface uploadDialog, int which) {
						
						if (TextUtils.isEmpty(editHost.getText()) || 
								TextUtils.isEmpty(editPort.getText()) || 
								TextUtils.isEmpty(editUser.getText()) ||
								TextUtils.isEmpty(editUser.getText())) {
							  toast("请将资料填写完整");
							  FtpMainActivity.this.finish();
							  return ;
						}
						try{
						    mFTPPort = Integer.parseInt(editPort.getText().toString().trim());
						}
						catch(NumberFormatException nfEx){
							nfEx.printStackTrace();
							toast("端口输入有误，请重试");
							return ;
						}
						mFTPHost = editHost.getText().toString().trim();
						mFTPUser = editUser.getText().toString().trim();
						mFTPPassword = editPasword.getText().toString().trim();
						Log.v(TAG, "mFTPHost #" + mFTPHost + " mFTPPort #" + mFTPPort 
								+ " mFTPUser #" + mFTPUser + " mFTPPassword #" + mFTPPassword);
						executeConnectRequest();
					}
				}).create();
	}
	
	private Dialog createLoadDialog() {

		View rootLoadView = getLayoutInflater().inflate(
				R.layout.dialog_load_file, null);
		mPbLoad = (ProgressBar) rootLoadView.findViewById(R.id.pbLoadFile);

		progressDialog = new AlertDialog.Builder(this).setTitle("请稍等片刻...")
				.setView(rootLoadView).setCancelable(false).create();

		progressDialog
				.setOnDismissListener(new DialogInterface.OnDismissListener() {

					@Override
					public void onDismiss(DialogInterface dialog) {
						// TODO Auto-generated method stub
						setLoadProgress(0);
					}
				});

		return progressDialog;
	}

	private Dialog createRenameDialog() {

		View rootLoadView = getLayoutInflater().inflate(R.layout.dialog_rename,
				null);
		final EditText edit = (EditText) rootLoadView
				.findViewById(R.id.editNewPath);

		return new AlertDialog.Builder(this)
				.setTitle("重命名")
				.setView(rootLoadView)
				.setPositiveButton("确定", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface uploadDialog, int which) {
						// TODO Auto-generated method stub
						if (!TextUtils.isEmpty(edit.getText())) {
							executeREANMERequest(edit.getText().toString());
						}
					}
				})
				.setNegativeButton("取消", new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface uploadDialog, int which) {
						// TODO Auto-generated method stub

					}
				}).create();
	}
	
	@Override
	protected void onResume() {
		super.onResume();
	}
	
	@Override
	protected void onDestroy() {
		mDameonRunning = false ;
		Thread thread = new Thread(mCmdFactory.createCmdDisConnect()) ;
		thread.start();
		//等待连接中断
		try {
			thread.join(2000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		mThreadPool.shutdownNow();
		super.onDestroy();
	}
	

	private Handler mHandler = new Handler() {

		@Override
		public void handleMessage(Message msg) {
			logv("mHandler --->" + msg.what);
			switch (msg.what) {
			case MSG_CMD_CONNECT_OK:
				toast("FTP服务器连接成功");
				if(mDameonThread == null){
					//启动守护进程
					mDameonThread = new Thread(new DameonFtpConnector());
					mDameonThread.setDaemon(true);
					mDameonThread.start();
				}
				executeLISTRequest();
				break;
			case MSG_CMD_CONNECT_FAILED:
				toast("FTP服务器连接失败，正在重新连接");
				executeConnectRequest();
				break;
			case MSG_CMD_LIST_OK:
				//toast("请求数据成功。");
				buildOrUpdateDataset();
				break;
			case MSG_CMD_LIST_FAILED:
				toast("请求数据失败");
				break;
			case MSG_CMD_CWD_OK:
				toast("请求数据成功");
				executeLISTRequest();
				break;
			case MSG_CMD_CWD_FAILED:
				toast("请求数据失败");
				break;
			case MSG_CMD_DELE_OK:
				toast("删除数据成功");
				executeLISTRequest();
				break;
			case MSG_CMD_DELE_FAILED:
				toast("删除数据失败");
				break;
			case MSG_CMD_RENAME_OK:
				toast("重命名数据成功");
				executeLISTRequest();
				break;
			case MSG_CMD_RENAME_FAILED:
				toast("重命名数据失败");
				break;
			default:
				break;
			}
		}
	};
	
	private void buildOrUpdateDataset() {
		//数据处理结束 
		mAdapter = new FtpFileAdapter(FtpMainActivity.this, datas);
		mListView.setAdapter(mAdapter);
		mAdapter.notifyDataSetChanged();
		mAdapter = null;
	}

	private void executeConnectRequest() {
		mThreadPool.execute(mCmdFactory.createCmdConnect());
	}

	private void executeDisConnectRequest() {
		mThreadPool.execute(mCmdFactory.createCmdDisConnect());
	}

	private void executePWDRequest() {
		mThreadPool.execute(mCmdFactory.createCmdPWD());
	}

	private void executeLISTRequest() {
		mThreadPool.execute(mCmdFactory.createCmdLIST());
	}

	private void executeCWDRequest(String path) {
		mThreadPool.execute(mCmdFactory.createCmdCWD(path));
	}

	private void executeDELERequest(String path, boolean isDirectory) {
		mThreadPool.execute(mCmdFactory.createCmdDEL(path, isDirectory));
	}

	private void executeREANMERequest(String newPath) {
		mThreadPool.execute(mCmdFactory.createCmdRENAME(newPath));
	}

	private void logv(String log) {
		Log.v(TAG, log);
	}

	private void toast(String hint) {
		
		Toast.makeText(this, hint, Toast.LENGTH_SHORT).show();
	}
    
	//本地文件选择器
	private void openFileDialog() {
		initDialog();
		uploadDialog = new AlertDialog.Builder(this).create();
		Window window = uploadDialog.getWindow();
		WindowManager.LayoutParams lp = window.getAttributes();
		window.setAttributes(lp);
		uploadDialog.show();
		uploadDialog.getWindow().setContentView(fileChooserView,
				new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.MATCH_PARENT, RelativeLayout.LayoutParams.MATCH_PARENT));
	}

	private void initDialog() {
		fileChooserView = getLayoutInflater().inflate(
				R.layout.filechooser_show, null);
		fileChooserView.findViewById(R.id.imgBackFolder).setOnClickListener(
				mClickListener);
		mTvPath = (TextView) fileChooserView.findViewById(R.id.tvPath);
		mGridView = (GridView) fileChooserView.findViewById(R.id.gvFileChooser);
		mGridView.setEmptyView(fileChooserView.findViewById(R.id.tvEmptyHint));
		mGridView.setOnItemClickListener(mItemClickListener);
		mGridView.setOnItemLongClickListener(lItemClickListener);
		setGridViewAdapter(mLocalRootPath);
	}

	private void setGridViewAdapter(String filePath) {
		updateFileItems(filePath);
		mUploadAdapter = new UploadFileChooserAdapter(this, mUploadFileList);
		mGridView.setAdapter(mUploadAdapter);
	}

	private void updateFileItems(String filePath) {
		mLastFilePath = filePath;
		mTvPath.setText(mLastFilePath);

		if (mUploadFileList == null)
			mUploadFileList = new ArrayList<FileInfo>();
		if (!mUploadFileList.isEmpty())
			mUploadFileList.clear();

		File[] files = folderScan(filePath);

		for (int i = 0; i < files.length; i++) {
			if (files[i].isHidden()) 
				continue;

			String fileAbsolutePath = files[i].getAbsolutePath();
			String fileName = files[i].getName();
			boolean isDirectory = false;
			if (files[i].isDirectory()) {
				isDirectory = true;
			}
			FileInfo fileInfo = new FileInfo(fileAbsolutePath, fileName,
					isDirectory);

			mUploadFileList.add(fileInfo);
		}
		
		if (mUploadAdapter != null)
			mUploadAdapter.notifyDataSetChanged();
	}

	private File[] folderScan(String path) {
		File file = new File(path);
		File[] files = file.listFiles();
		return files;
	}
	
	//上传文件
	private AdapterView.OnItemClickListener mItemClickListener = new OnItemClickListener() {
		public void onItemClick(AdapterView<?> adapterView, View view,
				int position, long id) {
			FileInfo fileInfo = (FileInfo) (((UploadFileChooserAdapter) adapterView
					.getAdapter()).getItem(position));
			if (fileInfo.isDirectory()) {
				updateFileItems(fileInfo.getFilePath());
			} else {
				showDialog(DIALOG_LOAD);
				new CmdUpload().execute(fileInfo.getFilePath());
			}
		}
	};
	
	//上传文件夹
	private AdapterView.OnItemLongClickListener lItemClickListener = new OnItemLongClickListener() {
		@Override
		public boolean onItemLongClick(AdapterView<?> adapterView, View view,
				int position, long id) {
			FileInfo fileInfo = (FileInfo) (((UploadFileChooserAdapter) adapterView
					.getAdapter()).getItem(position));
			if (fileInfo.isDirectory()) {
				showDialog(DIALOG_LOAD);
				new CmdUploadFolder().execute(fileInfo.getFilePath());
			} 
			// TODO Auto-generated method stub
			return true;
		}
	}; 
	//本地目录返回
	private View.OnClickListener mClickListener = new OnClickListener() {
		public void onClick(View v) {
			switch (v.getId()) {
			case R.id.imgBackFolder:
				backProcess();
				break;
			}
		}
	};

	public void backProcess() {
		if (!mLastFilePath.equals(mLocalRootPath)) {
			File thisFile = new File(mLastFilePath);
			String parentFilePath = thisFile.getParent();
			updateFileItems(parentFilePath);
		} else {
			setResult(RESULT_CANCELED);
			uploadDialog.dismiss();
		}
	}

	public void setLoadProgress(int progress) {
		if (mPbLoad != null) {
			mPbLoad.setProgress(progress);
		}
	}

	private static String getParentRootPath() {
		if (Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			if (mPath != null) {
				return mPath;
			} else {
				mPath = Environment.getExternalStorageDirectory().getAbsolutePath() + File.separator + OLIVE_DIR_NAME;
				File rootFile = new File(mPath);
				if (!rootFile.exists()) {
					rootFile.mkdir();
				}
				return mPath;
			}
		}
		return null;
	}
	
	public class CmdFactory {

		public FtpCmd createCmdConnect() {
			return new CmdConnect();
		}

		public FtpCmd createCmdDisConnect() {
			return new CmdDisConnect();
		}

		public FtpCmd createCmdPWD() {
			return new CmdPWD();
		}

		public FtpCmd createCmdLIST() {
			return new CmdLIST();
		}

		public FtpCmd createCmdCWD(String path) {
			return new CmdCWD(path);
		}

		public FtpCmd createCmdDEL(String path, boolean isDirectory) {
			return new CmdDELE(path, isDirectory);
		}

		public FtpCmd createCmdRENAME(String newPath) {
			return new CmdRENAME(newPath);
		}
	}

	//连接服务器
	public class DameonFtpConnector implements Runnable {

		@Override
		public void run() {
			Log.v(TAG, "DameonFtpConnector --- run");
			while (mDameonRunning) {
				if (mFTPClient != null && !mFTPClient.isConnected()) {
					try {
						mFTPClient.connect(mFTPHost, mFTPPort);
						mFTPClient.login(mFTPUser, mFTPPassword);
						
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				try {
					Thread.sleep(MAX_DAMEON_TIME_WAIT);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	public abstract class FtpCmd implements Runnable {

		public abstract void run();

	}

	public class CmdConnect extends FtpCmd {
		@Override
		public void run() {
			boolean errorAndRetry = false ;  
			try {
				String[] welcome = mFTPClient.connect(mFTPHost, mFTPPort);
				if (welcome != null) {
					for (String value : welcome) {
						logv("connect " + value);
					}
				}
				mFTPClient.login(mFTPUser, mFTPPassword);
				mHandler.sendEmptyMessage(MSG_CMD_CONNECT_OK);
			}catch (IllegalStateException illegalEx) {
				illegalEx.printStackTrace();
				mHandler.sendEmptyMessageDelayed(11, 1000);
			}catch (IOException ex) {
				ex.printStackTrace();
				mHandler.sendEmptyMessageDelayed(12, 1500);
			}catch (FTPIllegalReplyException e) {
				e.printStackTrace();
			}catch (FTPException e) {
				e.printStackTrace();
				errorAndRetry = true ;
			}
			if(errorAndRetry && mDameonRunning){
				mHandler.sendEmptyMessageDelayed(MSG_CMD_CONNECT_FAILED, 2000);
			}
		}
	}

	public class CmdDisConnect extends FtpCmd {

		@Override
		public void run() {
			if (mFTPClient != null) {
				try {
					mFTPClient.disconnect(true);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}
		}
	}

	public class CmdPWD extends FtpCmd {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				String pwd = mFTPClient.currentDirectory();
				logv("pwd --- > " + pwd);
			} catch (Exception ex) {
				ex.printStackTrace();
			}
		}
	}

	public class CmdLIST extends FtpCmd {
		
		@Override
		public void run() {
			// TODO Auto-generated method stub
			try {
				//返回上一层目录
				if(isBack){
					mFTPClient.changeDirectoryUp();
				}
				//重新加载到listview
				mCurrentPWD = mFTPClient.currentDirectory();
				FTPFile[] ftpFiles = mFTPClient.list();
				synchronized (mLock) {
					mFileList.clear();
					mFileList.addAll(Arrays.asList(ftpFiles));
					datas=new FTPFile[mFileList.size()];
			    	System.arraycopy(mFileList.toArray(), 0, datas, 0, mFileList.size());
				}
				mHandler.sendEmptyMessage(MSG_CMD_LIST_OK);
				isBack=false;

			} catch (Exception ex) {
				mHandler.sendEmptyMessage(MSG_CMD_LIST_FAILED);
				ex.printStackTrace();
			}
		}
	}

	public class CmdCWD extends FtpCmd {

		String realivePath;

		public CmdCWD(String path) {
			realivePath = path;
		}

		@Override
		public void run() {
			try {
				mFTPClient.changeDirectory(realivePath);
				mHandler.sendEmptyMessage(MSG_CMD_CWD_OK);
			} catch (Exception ex) {
				mHandler.sendEmptyMessage(MSG_CMD_CWD_FAILED);
				ex.printStackTrace();
			}
		}
	}

	public class CmdDELE extends FtpCmd {

		String realivePath;
		boolean isDirectory;

		public CmdDELE(String path, boolean isDirectory) {
			realivePath = path;
			this.isDirectory = isDirectory;
		}

		@Override
		public void run() {
			try {
				if (isDirectory) {
					mFTPClient.deleteDirectory(realivePath);
				} else {
					mFTPClient.deleteFile(realivePath);
				}
				mHandler.sendEmptyMessage(MSG_CMD_DELE_OK);
			} catch (Exception ex) {
				mHandler.sendEmptyMessage(MSG_CMD_DELE_FAILED);
				ex.printStackTrace();
			}
		}
	}

	public class CmdRENAME extends FtpCmd {

		String newPath;

		public CmdRENAME(String newPath) {
			this.newPath = newPath;
		}

		@Override
		public void run() {
			try {
				mFTPClient.rename(mFileList.get(mSelectedPosistion).getName(),
						newPath);
				mHandler.sendEmptyMessage(MSG_CMD_RENAME_OK);
			} catch (Exception ex) {
				mHandler.sendEmptyMessage(MSG_CMD_RENAME_FAILED);
				ex.printStackTrace();
			}
		}
	}

	//下载文件
	public class CmdDownLoad extends AsyncTask<Void, Integer, Boolean> {

		public CmdDownLoad() {

		}

		@Override
		protected Boolean doInBackground(Void... params) {
			try {
				String localPath = getParentRootPath() + File.separator
						+ mFileList.get(mSelectedPosistion).getName();
				mFTPClient.download(
						mFileList.get(mSelectedPosistion).getName(),
						new File(localPath),
						new DownloadFTPDataTransferListener(mFileList.get(
								mSelectedPosistion).getSize()));
			} catch (Exception ex) {
				ex.printStackTrace();
				return false;
			}

			return true;
		}

		protected void onProgressUpdate(Integer... progress) {

		}

		protected void onPostExecute(Boolean result) {
			toast(result ? "下载成功" : "下载失败");
			progressDialog.dismiss();
		}
	}
	
	//下载文件夹
	public class CmdDownloadFolder extends AsyncTask<Void, Integer, Boolean> {

		public CmdDownloadFolder() {

		}
		@Override
		protected Boolean doInBackground(Void... params) {
			try {
				String localPath = getParentRootPath();
				Log.e("localpath", "        "+localPath);
				//downloadFolder(mFTPClient, localPath);
				
				downloadFolder(mFTPClient, localPath,mCurrentPWD +"/"+mFileList.get(mSelectedPosistion).getName());
			} catch (Exception ex) {
				ex.printStackTrace();
				return false;
			}

			return true;
		}

		protected void onProgressUpdate(Integer... progress) {
			
		}

		protected void onPostExecute(Boolean result) {
			toast(result ? "下载成功" : "下载失败");
			progressDialog.dismiss();
		}
	}
	
	//上传文件
	public class CmdUpload extends AsyncTask<String, Integer, Boolean> {

		String path;

		public CmdUpload() {

		}

		@Override
		protected Boolean doInBackground(String... params) {
			path = params[0];
			try {
				File file = new File(path);
				mFTPClient.upload(file, new DownloadFTPDataTransferListener(
						file.length()));
			} catch (Exception ex) {
				ex.printStackTrace();
				return false;
			}

			return true;
		}

		protected void onProgressUpdate(Integer... progress) {

		}

		protected void onPostExecute(Boolean result) {
			toast(result ? path + "上传成功" : "上传失败");
			progressDialog.dismiss();
		}
	}
	//上传文件夹
	public class CmdUploadFolder extends AsyncTask<String, Integer, Boolean> {

		String path;

		public CmdUploadFolder() {

		}

		@Override
		protected Boolean doInBackground(String... params) {
			path = params[0];
				File file = new File(path);
				try {
					uploadFolder(mFTPClient,file,mCurrentPWD,false);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			
			return true;
		}

		protected void onProgressUpdate(Integer... progress) {

		}

		protected void onPostExecute(Boolean result) {
			toast(result ? path + "上传成功" : "上传失败");
			progressDialog.dismiss();
		}
	}
	
	private class DownloadFTPDataTransferListener implements
			FTPDataTransferListener {

		private int totolTransferred = 0;
		private long fileSize = -1;

		public DownloadFTPDataTransferListener(long fileSize) {
			this.fileSize = fileSize;
		}

		@Override
		public void aborted() {
			// TODO Auto-generated method stub
			logv("FTPDataTransferListener : aborted");
		}

		@Override
		public void completed() {
			// TODO Auto-generated method stub
			logv("FTPDataTransferListener : completed");
			setLoadProgress(mPbLoad.getMax());
		}

		@Override
		public void failed() {
			// TODO Auto-generated method stub
			logv("FTPDataTransferListener : failed");
		}

		@Override
		public void started() {
			// TODO Auto-generated method stub
			logv("FTPDataTransferListener : started");
		}

		@Override
		public void transferred(int length) {
			totolTransferred += length;
			float percent = (float) totolTransferred / this.fileSize;
			logv("FTPDataTransferListener : transferred # percent @@" + percent);
			setLoadProgress((int) (percent * mPbLoad.getMax()));
		}
	} 
	
	 /** 
     * 上传目录 
     */  
    private void uploadFolder(FTPClient client, File file,String path,  
            boolean del) throws Exception {
        client.changeDirectory(path);  
        String dir = file.getName(); // 当前目录名称   
        client.createDirectory(dir); // 创建目录  
     
        File[] files = file.listFiles(); // 获取当前文件夹所有文件及目录  
        for (int i = 0; i < files.length; i++) {  
        	file = files[i];  
            if (file.isDirectory()) { // 如果是目录，则递归上传  
                uploadFolder(client, file,path+"/"+dir,del);
            } else { // 如果是文件，直接上传
            	client.changeDirectory(path+"/"+dir);
                client.upload(file,new DownloadFTPDataTransferListener(
						file.length()));  
                if (del) { // 删除源文件  
                    file.delete();  
                }  
            }
        } 
        	//client.changeDirectoryUp();
    }  
	
	/** 
     * 下载文件夹 
     */  
    private void downloadFolder(FTPClient client, String localDir,String path)  
            throws Exception {  
        client.changeDirectory(path);
        // 在本地创建当前下载的文件夹  
        File folder = new File(localDir + "/" + new File(path).getName());  
        if (!folder.exists()) {  
            folder.mkdirs();  
        }  
        localDir = folder.getAbsolutePath();  
        FTPFile[] files = client.list(); 
        String name = null;  
        for (FTPFile file : files) {
            name = file.getName();  
            // 排除隐藏目录  
            if (".".equals(name) || "..".equals(name)) {  
                continue;  
            }  
            if (file.getType() == FTPFile.TYPE_DIRECTORY) { // 递归下载子目录 
                downloadFolder(client,localDir,path+"/"+file.getName());
            } else if (file.getType() == FTPFile.TYPE_FILE) { // 下载文件  
                client.download(name, new File(localDir + "/" + name),new DownloadFTPDataTransferListener(mFileList.get(
						mSelectedPosistion).getSize()));  
            }  
        }  
        client.changeDirectoryUp();  
    }  
	
	
	 /*监听对话框点击事件*/  
	  DialogInterface.OnClickListener listener = new DialogInterface.OnClickListener()  
	  {  
	       public void onClick(DialogInterface dialog, int which)  
	       {  
	            switch (which)  
	            {  
	            case AlertDialog.BUTTON_POSITIVE:// "确认"退出程序  
	                finish();  
	                break;  
	            case AlertDialog.BUTTON_NEGATIVE:// "取消"取消对话框  
	                break;  
	            default:  
	                break;  
	          }  
	        }  
	    }; 
	
	@Override
	public boolean onKeyUp(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_BACK:
				if(mCurrentPWD.equals("/")){
					
					AlertDialog isExit = new AlertDialog.Builder(this).create();  
					isExit.setTitle("系统提示");    
			        isExit.setMessage("确定要退出吗？");  
				    isExit.setButton("确定", listener);  
				    isExit.setButton2("取消", listener);  
				    isExit.show();
				    return true;
					
				}
				
				isBack=true;
				executeLISTRequest();
				return true;
			
		default:
			break;
		}
		return super.onKeyUp(keyCode, event);
	}

	@Override
	public void onClick(View arg0) {
		// TODO Auto-generated method stub
		
	}
	
}

