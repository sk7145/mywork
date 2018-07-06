package com.mstar.ftp3.adapter;

import it.sauronsoftware.ftp4j.FTPFile;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import com.mstar.ftp3.R;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.sip.SipAudioCall.Listener;
import android.text.format.Formatter;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class FtpFileAdapter extends BaseAdapter {
	
	
	private static String TAG = "FtpFileAdapter" ;
	private FTPFile[] datas = null;
	LayoutInflater mInfater = null;
	private Context mContext = null ;
	
	//匹配图标时用到的文件类型
	String[] fileTypes=new String[]{"apk","avi","bat","bin","bmp","chm","css","dat","dll","doc","docx","dos","dvd","gif","html","ifo","inf","iso"
			,"java","jpeg","jpg","log","m4a","mid","mov","movie","mp2","mp2v","mp3","mp4","mpe","mpeg","mpg","pdf","php","png","ppt","pptx","psd","rar","tif","ttf"
			,"txt","wav","wma","wmv","xls","xlsx","xml","xsl","zip"
		};
	
 	
 
 		LayoutInflater mInflater;
 		public FtpFileAdapter(Context context,FTPFile[] datas) {
 			this.mContext = context;
 			this.datas = datas;
 			mInflater = LayoutInflater.from(context);
 		}

 		// TODO getView
 		@Override
 		public View getView(int position, View convertView, ViewGroup parent) {
 			ViewHolder holder;
 			if (convertView == null) {
 				convertView = mInflater.inflate(R.layout.ftp_file_item,
 						null);
 				holder = new ViewHolder(convertView);
 				convertView.setTag(holder);
 			} else {
 				holder = (ViewHolder) convertView.getTag();
 			}
 			// 从arraylist集合里取出当前行数据；
 			FTPFile file=datas[position];

 			// 为页面控件设置数据
 			if(file.getType() == FTPFile.TYPE_DIRECTORY){
 				holder.appIcon.setImageResource(R.drawable.folder);
 			}else{
 				holder.appIcon.setImageResource(R.drawable.file);
 				String name=file.getName();
 				int pointIndex=name.lastIndexOf(".");
 				if(pointIndex!=-1){
 				String type=name.substring(pointIndex+1).toLowerCase();
 					for (int i = 0; i < fileTypes.length; i++) {
						if(type.equals(fileTypes[i])){
							try {
								int resId = mContext.getResources().getIdentifier(type, "drawable" , mContext.getPackageName());
								holder.appIcon.setImageResource(resId);
							} catch (Exception e) {
								e.printStackTrace();
							}
							
						}
					}
 				}
 				
 			}
 			holder.tvName.setText(file.getName());
 			holder.tvSize.setText(Formatter.formatFileSize(mContext, file.getSize()));
 			holder.tvDuration.setText(makeSimpleDateStringFromLong(file.getModifiedDate().getTime()));
 			
 			return convertView;
 		}

 		@Override
 		public int getCount() {
 			return datas.length;
 		}

 		@Override
 		public Object getItem(int position) {
 			return datas[position];
 		}

 		@Override
 		public long getItemId(int position) {
 			return position;
 		}

 		static class ViewHolder 
 		{
 			ImageView appIcon;
 			TextView tvName;
 			TextView tvSize;
 	        TextView tvDuration ;
 	        
 			public ViewHolder(View view)
 			{
 				this.appIcon = (ImageView) view.findViewById(R.id.imgIcon);
 				this.tvName = (TextView) view.findViewById(R.id.tvName);
 				this.tvSize = (TextView) view.findViewById(R.id.tvSize);
 				this.tvDuration = (TextView) view.findViewById(R.id.tvDuration);
 			}
 		}

 		private static final String SIMPLE_FORMAT_SHOW_TIME = "yyyy-MM-dd";

 		private static SimpleDateFormat sSimpleDateFormat = new SimpleDateFormat(SIMPLE_FORMAT_SHOW_TIME);
 		
 		//时间格式转换
 		public static CharSequence makeSimpleDateStringFromLong(long inTimeInMillis) {
 			return sSimpleDateFormat.format(new Date(inTimeInMillis));
 		}
}