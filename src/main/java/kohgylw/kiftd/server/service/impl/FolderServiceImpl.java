package kohgylw.kiftd.server.service.impl;

import kohgylw.kiftd.server.service.*;
import org.springframework.stereotype.*;
import kohgylw.kiftd.server.mapper.*;
import javax.annotation.*;
import javax.servlet.http.*;
import kohgylw.kiftd.server.enumeration.*;
import kohgylw.kiftd.server.model.*;
import kohgylw.kiftd.server.util.*;
import java.util.*;

@Service
public class FolderServiceImpl implements FolderService {
	@Resource
	private FolderMapper fm;
	@Resource
	private NodeMapper nm;
	@Resource
	private FolderUtil fu;
	@Resource
	private UsersMapper um;
	@Resource
	private LogUtil lu;

	public String newFolder(final HttpServletRequest request) {
		final String parentId = request.getParameter("parentId");
		final String folderName = request.getParameter("folderName");
		final String folderConstraint = request.getParameter("folderConstraint");
		final String account = (String) request.getSession().getAttribute("ACCOUNT");

		Folder folder = fm.queryById(parentId);
		if (!folder.getFolderName().equals("ROOT")){
			if (!folder.getFolderCreator().equals(account)){
				return "errorCreator";
			}
		}


		if (!ConfigureReader.instance().authorized(account, AccountAuth.CREATE_NEW_FOLDER)) {
			return "noAuthorized";
		}
		if (parentId == null || folderName == null || parentId.length() <= 0 || folderName.length() <= 0) {
			return "errorParameter";
		}
		if (!TextFormateUtil.instance().matcherFolderName(folderName) || folderName.indexOf(".") == 0) {
			return "errorParameter";
		}
		final Folder parentFolder = this.fm.queryById(parentId);
		if (parentFolder == null) {
			return "errorParameter";
		}
		if (fm.queryByParentId(parentId).parallelStream().anyMatch((e) -> e.getFolderName().equals(folderName))) {
			return "nameOccupied";
		}
		Folder f = new Folder();
		// 设置子文件夹约束等级，不允许子文件夹的约束等级比父文件夹低
		int pc = parentFolder.getFolderConstraint();
		if (folderConstraint != null) {
			try {
				int ifc = Integer.parseInt(folderConstraint);
				if (ifc > 0 && account == null) {
					return "errorParameter";
				}
				if (ifc < pc) {
					return "errorParameter";
				} else {
					f.setFolderConstraint(ifc);
				}
			} catch (Exception e) {
				// TODO: handle exception
				return "errorParameter";
			}
		} else {
			return "errorParameter";
		}
		f.setFolderId(UUID.randomUUID().toString());
		f.setFolderName(folderName);
		f.setFolderCreationDate(ServerTimeUtil.accurateToDay());
		f.setFolderSize("0");
		if (account != null) {
			f.setFolderCreator(account);
		} else {
			f.setFolderCreator("匿名用户");
		}
		f.setFolderParent(parentId);
		int i = 0;
		while (true) {
			try {
				final int r = this.fm.insertNewFolder(f);
				if (r > 0) {
					this.lu.writeCreateFolderEvent(request, f);
					return "createFolderSuccess";
				}
				break;
			} catch (Exception e) {
				f.setFolderId(UUID.randomUUID().toString());
				i++;
			}
			if (i >= 10) {
				break;
			}
		}
		return "cannotCreateFolder";
	}

	public String deleteFolder(final HttpServletRequest request) {
		final String folderId = request.getParameter("folderId");
		final String account = (String) request.getSession().getAttribute("ACCOUNT");
		Users users = (Users) request.getSession().getAttribute("users");

		Users users1 = new Users();
		String fileSize = "";
		String realUserName = "";
		String realFileSize = "";

		if (!ConfigureReader.instance().authorized(account, AccountAuth.DELETE_FILE_OR_FOLDER)) {
			return "noAuthorized";
		}
		if (folderId == null || folderId.length() <= 0) {
			return "errorParameter";
		}
		Folder folder = this.fm.queryById(folderId);
		if (folder == null) {
			return "deleteFolderSuccess";
		}
		final List<Folder> l = this.fu.getParentList(folderId);

		//TODO 用户是admin时可能删除别人的文件，这样就要减别人的空间
		if (!folder.getFolderCreator().equals(account)){
			users1 = um.queryByUsername(folder.getFolderCreator());
			fileSize = Integer.parseInt(users1.getFILESIZE()) - Integer.parseInt(folder.getFolderSize()) + "";
			realUserName = users1.getUSERNAME();
			realFileSize = users.getFILESIZE();
		} else {
			fileSize = Integer.parseInt(users.getFILESIZE()) - Integer.parseInt(folder.getFolderSize()) + "";
			realUserName = users.getUSERNAME();
			realFileSize = fileSize;
		}

		//fileSize = Integer.parseInt(users.getFILESIZE()) - Integer.parseInt(folder.getFolderSize()) + "";
		String folderSize = folder.getFolderSize();
		if (this.fu.deleteAllChildFolder(folderId) > 0 && um.updateSizeByUsername(fileSize, realUserName) > 0) {
			//TODO 删除文件夹后 向上遍历修改文件夹的大小
			boolean ifParent = true;
						/*if (folder.getFolderParent() == null || folder.getFolderParent() == "null"){
							ifParent = false;
						}*/
			while (ifParent){
				if (folder.getFolderId().equals("root")){
					ifParent = false;
				}
				String parentSize = Integer.parseInt(folder.getFolderSize()) - Integer.parseInt(folderSize) + "";
				fm.updateFolderSizeById(parentSize, folder.getFolderId());
				folder = fm.queryById(folder.getFolderParent());
			}
			users.setFILESIZE(realFileSize);
			this.lu.writeDeleteFolderEvent(request, folder, l);
			return "deleteFolderSuccess";
		}
		return "cannotDeleteFolder";
	}

	public String renameFolder(final HttpServletRequest request) {
		final String folderId = request.getParameter("folderId");
		final String newName = request.getParameter("newName");
		final String folderConstraint = request.getParameter("folderConstraint");
		final String account = (String) request.getSession().getAttribute("ACCOUNT");
		if (!ConfigureReader.instance().authorized(account, AccountAuth.RENAME_FILE_OR_FOLDER)) {
			return "noAuthorized";
		}
		if (folderId == null || folderId.length() <= 0 || newName == null || newName.length() <= 0) {
			return "errorParameter";
		}
		if (!TextFormateUtil.instance().matcherFolderName(newName) || newName.indexOf(".") == 0) {
			return "errorParameter";
		}
		final Folder folder = this.fm.queryById(folderId);
		if (folder == null) {
			return "errorParameter";
		}
		final Folder parentFolder = this.fm.queryById(folder.getFolderParent());
		int pc = parentFolder.getFolderConstraint();
		if (folderConstraint != null) {
			try {
				int ifc = Integer.parseInt(folderConstraint);
				if (ifc > 0 && account == null) {
					return "errorParameter";
				}
				if (ifc < pc) {
					return "errorParameter";
				} else {
					Map<String, Object> map = new HashMap<>();
					map.put("newConstraint", ifc);
					map.put("folderId", folderId);
					fm.updateFolderConstraintById(map);
					changeChildFolderConstraint(folderId, ifc);
					if (!folder.getFolderName().equals(newName)) {
						if (fm.queryByParentId(parentFolder.getFolderId()).parallelStream()
								.anyMatch((e) -> e.getFolderName().equals(newName))) {
							return "nameOccupied";
						}
						Map<String, String> map2 = new HashMap<String, String>();
						map2.put("folderId", folderId);
						map2.put("newName", newName);
						if (this.fm.updateFolderNameById(map2) == 0) {
							return "errorParameter";
						}
					}
					this.lu.writeRenameFolderEvent(request, folder, newName, folderConstraint);
					return "renameFolderSuccess";
				}
			} catch (Exception e) {
				// TODO: handle exception
				return "errorParameter";
			}
		} else {
			return "errorParameter";
		}
	}

	/**
	 * 
	 * <h2>迭代修改子文件夹约束</h2>
	 * <p>
	 * 当某一文件夹的约束被修改时，其所有子文件夹的约束等级均不得低于其父文件夹。 例如：
	 * 父文件夹的约束等级改为1（仅小组）时，所有约束等级为0（公开的）的子文件夹的约束等级也会提升为1， 而所有约束等级为2（仅自己）的子文件夹则不会受影响。
	 * </p>
	 * 
	 * @author 青阳龙野(kohgylw)
	 * @param folderId
	 *            要修改的文件夹ID
	 * @param c
	 *            约束等级
	 */
	private void changeChildFolderConstraint(String folderId, int c) {
		List<Folder> cfs = fm.queryByParentId(folderId);
		for (Folder cf : cfs) {
			if (cf.getFolderConstraint() < c) {
				Map<String, Object> map = new HashMap<String, Object>();
				map.put("newConstraint", c);
				map.put("folderId", cf.getFolderId());
				fm.updateFolderConstraintById(map);
			}
			changeChildFolderConstraint(cf.getFolderId(), c);
		}
	}

}
